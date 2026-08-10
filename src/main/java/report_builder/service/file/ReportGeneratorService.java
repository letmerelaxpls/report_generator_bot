package report_builder.service.file;

import org.springframework.core.io.ClassPathResource;
import report_builder.model.ActivityRecord;
import java.util.List;

public interface ReportGeneratorService {
    void fillReport(ClassPathResource templatePath, String outputPath, List<ActivityRecord> records);
}
