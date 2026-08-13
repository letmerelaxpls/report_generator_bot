package report_builder.controller;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import report_builder.dto.CategoryDto;
import report_builder.dto.RecordRequestDto;
import report_builder.service.activityrecord.ActivityRecordService;
import report_builder.service.category.CategoryService;
import report_builder.service.telegram.BotService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WebAppController {
    private final CategoryService categoryService;
    private final ActivityRecordService activityRecordService;
    private final BotService botService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        List<CategoryDto> list = categoryService.getAllCategories().stream()
                .map(c -> new CategoryDto(
                        c.getId(),
                        c.getName(),
                        c.getParent() != null ? c.getParent().getId() : null
                ))
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/records")
    public ResponseEntity<Void> updateRecord(@RequestBody RecordRequestDto requestDto) {
        activityRecordService.updateRecord(requestDto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reports/generate")
    public ResponseEntity<Void> generateReport(
            @RequestParam Long chatId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {

        botService.sendReport(chatId, null, month);
        return ResponseEntity.ok().build();
    }

}
