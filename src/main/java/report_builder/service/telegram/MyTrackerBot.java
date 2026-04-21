package report_builder.service.telegram;

import report_builder.model.enums.CallbackData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import report_builder.model.enums.UserStateType;
import report_builder.state.UserState;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MyTrackerBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final BotService botService;
    @Value("${bot.token}")
    private String botToken;

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            UserState state = botService.getUserState(chatId);

            switch (state.type()) {
                case AWAITING_CATEGORY_NAME -> botService.saveNewCategory(chatId, text);
                case EDIT_NAME -> botService.updateCategoryName(chatId, state.targetId(), text);
                default -> botService.sendMainMenu(chatId);
            }
        }
        else if (update.hasCallbackQuery()) {
            CallbackQuery query = update.getCallbackQuery();
            String data = query.getData();
            Long chatId = query.getMessage().getChatId();
            Integer messageId = query.getMessage().getMessageId();

            String[] parts = data.split(":");
            CallbackData command = CallbackData.fromString(parts[0]);
            if (command == null) return;

            switch (command) {
                case ADD_START -> botService.editToCategoryMenu(chatId, messageId, LocalDate.now(), null);
                case BACK_TO_MAIN -> botService.editToMainMenu(chatId, messageId);
                case SHIFT_DAY -> {
                    LocalDate newDate = LocalDate.parse(parts[1]);
                    Long catId = extractCatId(parts);
                    botService.editToCategoryMenu(chatId, messageId, newDate, catId);
                }
                case SELECTED_CAT_T0_ADD -> {
                    Long catId = extractCatId(parts);
                    LocalDate selectedDate = LocalDate.parse(parts[1]);
                    botService.editToCategoryMenu(chatId, messageId, selectedDate, catId);
                }
                case REPORT_START -> botService.chooseReportMonth(chatId, messageId, LocalDate.now());
                case SHIFT_MONTH -> {
                    LocalDate newDate = LocalDate.parse(parts[1]);
                    botService.chooseReportMonth(chatId, messageId, newDate);
                }
                case SEND_REPORT -> botService.sendReport(chatId, messageId, LocalDate.parse(parts[1]));
                case ADD_TO_CATEGORY -> {
                    LocalDate selectedDate = LocalDate.parse(parts[1]);
                    Long catId = extractCatId(parts);
                    botService.addToCategory(chatId, messageId, selectedDate, catId);
                }
                case ADD_CATEGORY_START -> botService.prepareAddCategory(chatId, messageId);
                case CANCEL -> {
                    botService.clearUserState();
                    botService.editToMainMenu(chatId, messageId);
                }
                case SETTINGS -> botService.editToChooseSettingMenu(chatId, messageId);
                case MANAGE_CATEGORIES -> botService.editToManageCategoriesMenu(chatId, messageId, null);
                case SELECTED_CAT_TO_MANAGE -> {
                    Long catId = extractCatId(parts);
                    botService.editToManageCategoriesMenu(chatId, messageId, catId);
                }
                case EDIT_CATEGORY -> {
                    Long catId = extractCatId(parts);
                    botService.prepareEditCategory(chatId, messageId, catId);
                }
                case DELETE_CATEGORY -> {
                    Long catId = extractCatId(parts);
                    botService.deleteCategory(chatId, messageId, catId);
                }
            }
        }
    }

    private Long extractCatId(String[] parts) {
        return (parts.length > 2 && !parts[2].isEmpty()) ? Long.parseLong(parts[2]) : null;
    }
}
