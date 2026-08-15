package report_builder.controller;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import report_builder.dto.CategoryDto;
import report_builder.dto.RecordRequestDto;
import report_builder.service.activityrecord.ActivityRecordService;
import report_builder.service.category.CategoryService;
import report_builder.service.telegram.BotService;
import report_builder.security.TelegramAuthValidator;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WebAppController {
    private final CategoryService categoryService;
    private final ActivityRecordService activityRecordService;
    private final BotService botService;
    @Value("${bot.token}")
    private String botToken;
    @Value("${telegram.allowed-users}")
    private List<Long> WHITELIST_CHAT_IDS;

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories(
            @RequestHeader(value = "X-TG-INIT-DATA", required = false) String initData) {

        ResponseEntity<?> response = validateInitData(initData);
        if (response != null) {
            return response;
        }

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
    public ResponseEntity<?> updateRecord(
            @RequestHeader(value = "X-TG-INIT-DATA", required = false) String initData,
            @RequestBody RecordRequestDto requestDto) {
        ResponseEntity<?> response = validateInitData(initData);
        if (response != null) {
            return response;
        }

        activityRecordService.updateRecord(requestDto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reports/generate")
    public ResponseEntity<?> generateReport(
            @RequestHeader(value = "X-TG-INIT-DATA", required = false) String initData,
            @RequestParam Long chatId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        ResponseEntity<?> response = validateInitData(initData);
        if (response != null) {
            return response;
        }

        botService.sendReport(chatId, null, month);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<?> validateInitData(String initData) {
        if (initData == null || initData.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied: Missing initData");
        }

        if (!TelegramAuthValidator.isInitDataValid(initData, botToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Invalid hash signature");
        }

        Long userId = TelegramAuthValidator.extractUserId(initData);

        if (!WHITELIST_CHAT_IDS.contains(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: User ID not in whitelist");
        }
        return null;
    }

}
