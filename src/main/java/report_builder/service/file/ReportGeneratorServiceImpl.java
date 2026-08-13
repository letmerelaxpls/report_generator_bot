package report_builder.service.file;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import report_builder.model.ActivityRecord;
import report_builder.model.Category;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ReportGeneratorServiceImpl implements ReportGeneratorService {
    private static final Map<Integer, List<Integer>> AGGREGATION_RULES = Map.of(
            3, List.of(7, 10, 13, 22, 25, 28),
            4, List.of(8, 11, 14, 23, 26, 29)
    );
    private static final String FULL_DATE_FORMAT = "dd.MM.yyyy";
    private static final Integer START_ROW_INDEX = 3;

    public void fillReport(ClassPathResource templatePath, String outputPath, List<ActivityRecord> records) {
        Map<LocalDate, Map<Integer, Integer>> reportData = prepareReportData(records);

        try (InputStream is = templatePath.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            XWPFTable table = document.getTables().getFirst();
            int currentRow = START_ROW_INDEX;
            Map<Integer, Integer> grandTotals = new HashMap<>();

            for (var entry : reportData.entrySet()) {
                XWPFTableRow row = getOrCreateRow(table, currentRow++);

                writeToCell(row, 0, entry.getKey().format(DateTimeFormatter.ofPattern(FULL_DATE_FORMAT)));

                entry.getValue().forEach((col, val) -> {
                    writeToCell(row, col, String.valueOf(val));
                    grandTotals.merge(col, val, Integer::sum);
                });
            }

            XWPFTableRow footerRow = getOrCreateRow(table, Math.max(currentRow, 27));
            writeToCell(footerRow, 0, "Всього");

            grandTotals.forEach((col, val) -> {
                if (val > 0 && col > 0) {
                    writeToCell(footerRow, col, String.valueOf(val));
                }
            });

            saveToFile(document, outputPath);

        } catch (IOException e) {
            throw new RuntimeException("Could not generate the report", e);
        }
    }

    private void saveToFile(XWPFDocument document, String outputPath) {
        File outputFile = new File(outputPath);

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            document.write(fos);
        } catch (IOException e) {
            throw new RuntimeException("Could not save file to : " + outputPath, e);
        }
    }

    private void writeToCell(XWPFTableRow row, int cellIndex, String text) {
        XWPFTableCell cell = row.getCell(cellIndex);

        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }

        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(8);
        run.setFontFamily("Arial Narrow");
    }

    private Map<LocalDate, Map<Integer, Integer>> prepareReportData(List<ActivityRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        ActivityRecord::getDate,
                        TreeMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::aggregateDailyRecords)
                ));
    }

    private Map<Integer, Integer> aggregateDailyRecords(List<ActivityRecord> dayRecords) {
        Map<Integer, Integer> rowData = new HashMap<>();

        for (ActivityRecord record : dayRecords) {
            if (record.getCount() == 0) continue;

            Category current = record.getCategory();
            while (current != null) {
                if (current.getColumnIndex() != null) {
                    rowData.merge(current.getColumnIndex(), record.getCount(), Integer::sum);
                }
                current = current.getAggregateTo();
            }
        }

        AGGREGATION_RULES.forEach((target, sources) -> {
            int sum = sources.stream().mapToInt(c -> rowData.getOrDefault(c, 0)).sum();
            if (sum > 0) rowData.put(target, sum);
        });

        return rowData;
    }

    private XWPFTableRow getOrCreateRow(XWPFTable table, int index) {
        XWPFTableRow row = table.getRow(index);
        return (row != null) ? row : table.createRow();
    }
}
