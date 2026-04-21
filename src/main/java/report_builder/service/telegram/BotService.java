package report_builder.service.telegram;

import java.time.LocalDate;
import report_builder.state.UserState;

public interface BotService {
    void sendMainMenu(Long chatId);

    void editToMainMenu(Long chatId, Integer messageId);

    void editToCategoryMenu(Long chatId, Integer messageId, LocalDate date, Long selectedCatId);

    void addToCategory(Long chatId, Integer messageId, LocalDate selectedDate, Long selectedCatId);

    void chooseReportMonth(Long chatId, Integer messageId, LocalDate date);

    void sendReport(Long chatId, Integer messageId, LocalDate selectedMonth);

    void prepareAddCategory(Long chatId, Integer messageId);

    void saveNewCategory(Long chatId, String text);

    UserState getUserState(Long chatId);

    void clearUserState();

    void editToChooseSettingMenu(Long chatId, Integer messageId);

    void editToManageCategoriesMenu(Long chatId, Integer messageId, Long selectedCatId);

    void prepareEditCategory(Long chatId, Integer messageId, Long catId);

    void updateCategoryName(Long chatId, Long catId, String newName);

    void deleteCategory(Long chatId, Integer messageId, Long catId);
}
