package report_builder.service.telegram;

import java.time.LocalDate;

import report_builder.model.enums.CallbackData;
import report_builder.state.UserState;

public interface BotService {
    void sendMainMenu(Long chatId);

    void sendMessage(Long chatId, String text);

    void editToMainMenu(Long chatId, Integer messageId);

    void editToCategoryMenu(Long chatId, Integer messageId, LocalDate date,
                            Long parentId, Long selectedCatId, CallbackData operationType);

    void addToCategory(Long chatId, Integer messageId, LocalDate selectedDate, Long selectedCatId);

    void subtractFromCategory(Long chatId, Integer messageId, LocalDate selectedDate, Long selectedCatId);

    void prepareCustomQuantityInput(Long chatId, Integer messageId, LocalDate date, Long catId, CallbackData operationType);

    void saveCustomQuantity(Long chatId, Long catId, LocalDate date, int quantity, CallbackData operationType);

    void chooseReportMonth(Long chatId, Integer messageId, LocalDate date);

    void sendReport(Long chatId, Integer messageId, LocalDate selectedMonth);

    UserState getUserState(Long chatId);

    void clearUserState();

    void showCategoriesToManage(Long chatId, Integer messageId, Long selectedCatId);

    void prepareEditCategory(Long chatId, Integer messageId, Long catId);

    void updateCategoryName(Long chatId, Long catId, String newName);
}
