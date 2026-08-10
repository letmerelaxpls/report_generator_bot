package report_builder.service.telegram;

import report_builder.model.Category;
import report_builder.model.enums.CallbackData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import report_builder.service.category.CategoryService;
import report_builder.state.UserState;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MyTrackerBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final List<Long> WHITELIST_CHAT_IDS = List.of(501873018L, 670376103L);
    private final BotService botService;
    private final CategoryService categoryService;
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
            if (!checkAccess(chatId)) return;

            String text = update.getMessage().getText();
            UserState state = botService.getUserState(chatId);

            handleMessage(chatId, text, state);
        }
        else if (update.hasCallbackQuery()) {
            CallbackQuery query = update.getCallbackQuery();
            Long chatId = query.getMessage().getChatId();
            String data = query.getData();
            Integer messageId = query.getMessage().getMessageId();

            String[] parts = data.split(":");
            CallbackData command = CallbackData.fromString(parts[0]);
            if (command == null) return;

            handleCallbackQuery(chatId, messageId, parts, command);

        }
    }

    private void handleMessage(Long chatId, String text, UserState state) {
        switch (state.type()) {
            case EDIT_NAME -> botService.updateCategoryName(chatId, state.targetId(), text);
            case AWAITING_QUANTITY_INPUT ->  {
                try {
                    int quantity = Integer.parseInt(text);
                    if (quantity < 0) {
                        throw new NumberFormatException();
                    }
                    botService.saveCustomQuantity(chatId, state.targetId(),
                            state.date(), quantity, state.operationType());
                } catch (NumberFormatException e) {
                    botService.sendMessage(chatId, "⚠\uFE0F Будь ласка, введіть коректне ціле число.");
                }
            }
            default -> botService.sendMainMenu(chatId);
        }
    }

    private void handleCallbackQuery(Long chatId, Integer messageId,
                                     String[] parts, CallbackData command) {
        switch (command) {
            case ADD_START -> botService.editToCategoryMenu(chatId, messageId, LocalDate.now(),
                    null, null, CallbackData.ADD_TO_CATEGORY);
            case SUBTRACT_START -> botService.editToCategoryMenu(chatId, messageId, LocalDate.now(),
                    null, null, CallbackData.SUBTRACT_FROM_CATEGORY);
            case BACK_TO_MAIN -> botService.editToMainMenu(chatId, messageId);
            case SHIFT_DAY -> {
                LocalDate newDate = LocalDate.parse(parts[1]);
                Long catId = extractCatId(parts);
                CallbackData operationType = extractOperationType(parts);
                Long parentId = null;
                Category category = categoryService.getById(catId).orElseThrow();
                parentId = (category.getParent() != null)
                        ? category.getParent().getId()
                        : null;
                botService.editToCategoryMenu(chatId, messageId, newDate, parentId, catId, operationType);
            }
            case SELECTED_CAT_T0_ADD -> {
                Long catId = extractCatId(parts);
                LocalDate selectedDate = extractDate(parts);
                Category category = categoryService.getById(catId).orElseThrow();
                CallbackData operationType = extractOperationType(parts);

                if (!category.getSubCategories().isEmpty()) {
                    botService.editToCategoryMenu(chatId, messageId, selectedDate,
                            catId, null, operationType);
                } else {
                    Long parentId = (category.getParent() != null)
                            ? category.getParent().getId()
                            : null;
                    botService.editToCategoryMenu(chatId, messageId, selectedDate,
                            parentId, catId, operationType);
                }
            }
            case REPORT_START -> botService.chooseReportMonth(chatId, messageId, LocalDate.now());
            case SHIFT_MONTH -> {
                LocalDate newDate = extractDate(parts);
                botService.chooseReportMonth(chatId, messageId, newDate);
            }
            case SEND_REPORT -> botService.sendReport(chatId, messageId, extractDate(parts));
            case ADD_TO_CATEGORY -> {
                LocalDate selectedDate = extractDate(parts);
                Long catId = extractCatId(parts);
                botService.addToCategory(chatId, messageId, selectedDate, catId);
            }
            case SUBTRACT_FROM_CATEGORY -> {
                LocalDate selectedDate = extractDate(parts);
                Long catId = extractCatId(parts);
                botService.subtractFromCategory(chatId, messageId, selectedDate, catId);
            }
            case INPUT_CUSTOM_QUANTITY -> {
                Long selectedCatId = extractCatId(parts);
                LocalDate date = extractDate(parts);
                CallbackData operationType = CallbackData.valueOf(parts[3]);
                botService.prepareCustomQuantityInput(chatId, messageId, date, selectedCatId, operationType);
            }
            case CANCEL -> {
                botService.clearUserState();
                botService.editToMainMenu(chatId, messageId);
            }
            case MANAGE_CATEGORIES -> {
                Long selectedCatId = extractCatId(parts);
                botService.showCategoriesToManage(chatId, messageId, selectedCatId);
            }
            case EDIT_CATEGORY -> {
                Long catId = extractCatId(parts);
                botService.prepareEditCategory(chatId, messageId, catId);
            }
        }
    }

    private boolean checkAccess(Long chatId) {
        return WHITELIST_CHAT_IDS.stream()
                .anyMatch(c -> c.equals(chatId));
    }

    private Long extractCatId(String[] parts) {
        return (parts.length > 1) ? Long.parseLong(parts[2]) : null;
    }

    private LocalDate extractDate(String[] parts) {
        return LocalDate.parse(parts[1]);
    }

    private CallbackData extractOperationType(String[] parts) {
        return CallbackData.valueOf(parts[3]);
    }
}
