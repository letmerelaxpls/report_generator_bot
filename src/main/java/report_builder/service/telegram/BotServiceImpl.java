package report_builder.service.telegram;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import report_builder.model.ActivityRecord;
import report_builder.model.Category;
import report_builder.model.enums.CallbackData;
import report_builder.model.enums.UserStateType;
import report_builder.repository.activity.ActivityRepository;
import report_builder.service.category.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import report_builder.state.UserState;

@Service
@RequiredArgsConstructor
public class BotServiceImpl implements BotService {
    private static final String DATE_DAY_FORMAT = "dd.MM";
    private static final String DATE_MONTH_FORMAT = "MM.yyyy";
    private static final String DATE_FULL_FORMAT = "dd.MM.yyyy";
    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final TelegramClient telegramClient;
    private final CategoryService categoryService;
    private final ActivityRepository activityRepository;

    @Override
    public void sendMainMenu(Long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text("MAIN MENU:")
                .replyMarkup(getMainMenuKeyboard())
                .build();
        execute(message);
    }

    @Override
    public void editToMainMenu(Long chatId, Integer messageId) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text("MAIN MENU:")
                .replyMarkup(getMainMenuKeyboard())
                .build();
        execute(edit);
    }

    @Override
    public void editToCategoryMenu(Long chatId, Integer messageId, LocalDate date, Long selectedCatId) {

        List<InlineKeyboardRow> categories = getCategoriesButtons(CallbackData.SELECTED_CAT_T0_ADD, selectedCatId, date);
        List<InlineKeyboardRow> rows = new ArrayList<>(categories);

        rows.addAll(getDateNavigation(CallbackData.SHIFT_DAY, CallbackData.ADD_TO_CATEGORY,
                DATE_FULL_FORMAT, selectedCatId, date));

        String text = (selectedCatId == null)
                ? "     Choose a category.     "
                : "     Press on the date for confirmation"     ;

        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();

        execute(edit);
    }

    @Override
    public void addToCategory(Long chatId, Integer messageId, LocalDate selectedDate, Long selectedCatId) {
        if (selectedCatId == null) {
            return;
        }

        Category category = categoryService.getAllCategories().stream()
                .filter(c -> c.getId().equals(selectedCatId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find Category with id: "
                        + selectedCatId));

        ActivityRecord activityRecord = activityRepository.findByCategoryAndDate(category, selectedDate)
                .orElseGet(() -> {
                    ActivityRecord newRecord = new ActivityRecord();
                    newRecord.setCategory(category);
                    newRecord.setDate(selectedDate);
                    return newRecord;
                });

        activityRecord.setCount(activityRecord.getCount() + 1);
        activityRepository.save(activityRecord);

        String formattedDate = selectedDate.format(DateTimeFormatter.ofPattern(DATE_FULL_FORMAT));
        EditMessageText successMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(String.format("✅ <b>%s</b>: %d-th time for %s",
                        category.getName(), activityRecord.getCount(), formattedDate))
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuKeyboard())
                .build();

        execute(successMessage);
    }

    @Override
    public void chooseReportMonth(Long chatId, Integer messageId, LocalDate date) {
        EditMessageText messageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Choose the month of the report.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(getDateNavigation(CallbackData.SHIFT_MONTH,
                                CallbackData.SEND_REPORT, DATE_MONTH_FORMAT,
                                null, date))
                        .build())
                .build();

        execute(messageText);
    }

    @Override
    public void sendReport(Long chatId, Integer messageId, LocalDate selectedMonth) {
        LocalDate startOfMonth = selectedMonth.withDayOfMonth(1);
        LocalDate endOfMonth = selectedMonth.withDayOfMonth(selectedMonth.lengthOfMonth());

        List<ActivityRecord> records = activityRepository
                .findAllByDateBetween(startOfMonth, endOfMonth).stream()
                .sorted(Comparator.comparing(ActivityRecord::getDate))
                .toList();

        if (records.isEmpty()) {
            EditMessageText message = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text("There is no records for " + selectedMonth.format(DateTimeFormatter.ofPattern(DATE_MONTH_FORMAT)))
                    .replyMarkup(getMainMenuKeyboard())
                    .build();
            execute(message);
            return;
        }

        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append(String.format("\uD83D\uDCCA <b>Detailed report for %s %d</b>\n\n",
                selectedMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.UK), selectedMonth.getYear()));

        Map<LocalDate, List<ActivityRecord>> groupedByDate = records.stream()
                .collect(Collectors.groupingBy(ActivityRecord::getDate, TreeMap::new,
                        Collectors.toList()));

        groupedByDate.forEach(((date, activityRecords) -> {
            reportBuilder.append(String.format("\uD83D\uDCC5 <b>%s</b>:\n",
                    date.format(DateTimeFormatter.ofPattern(DATE_DAY_FORMAT))));
            for (ActivityRecord r: activityRecords) {
                reportBuilder.append(String.format("  • %s: %d\n",
                        r.getCategory().getName(), r.getCount()));
            }
            reportBuilder.append("\n");
        }));

        reportBuilder.append("---------------------------\n");
        reportBuilder.append("<b>Total for month:</b>\n");

        Map<String, Integer> totalStats = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategory().getName(),
                        Collectors.summingInt(ActivityRecord::getCount)
                ));

        totalStats.forEach((name, total) -> {
            reportBuilder.append(String.format("  • %s: <b>%d</b>\n", name, total));
        });

        EditMessageText reportMsg = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(reportBuilder.toString())
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuKeyboard())
                .build();

        execute(reportMsg);
    }

    @Override
    public void prepareAddCategory(Long chatId, Integer messageId) {
        userStates.put(chatId, new UserState(UserStateType.AWAITING_CATEGORY_NAME, null));

        EditMessageText messageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("📝 Enter the name for new category:")
                .replyMarkup(getCancelButton())
                .build();
        execute(messageText);
    }

    @Override
    public void saveNewCategory(Long chatId, String categoryName) {
        categoryService.addCategory(categoryName);

        clearUserState();

        SendMessage successMessage = SendMessage
                .builder()
                .chatId(chatId)
                .text("✅ Category<b>" + categoryName + "</b> was added successfully!")
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuKeyboard())
                .build();
        execute(successMessage);
    }

    @Override
    public UserState getUserState(Long chatId) {
        return userStates.getOrDefault(chatId, UserState.empty());
    }

    @Override
    public void clearUserState() {
        userStates.clear();
    }

    @Override
    public void editToChooseSettingMenu(Long chatId, Integer messageId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow actions = new InlineKeyboardRow(List.of(
                createButton("✏\uFE0F Change name \n або \n ❌ Delete",
                        CallbackData.MANAGE_CATEGORIES.name()),
                createButton("➕ Add category",
                        CallbackData.ADD_CATEGORY_START.name())
        ));

        rows.add(actions);
        rows.add(getReturnButton());

        EditMessageText messageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Choose the setting.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();

        execute(messageText);
    }

    @Override
    public void editToManageCategoriesMenu(Long chatId, Integer messageId, Long selectedCatId) {

        List<InlineKeyboardRow> categories = getCategoriesButtons(CallbackData.SELECTED_CAT_TO_MANAGE,
                selectedCatId, null);
        List<InlineKeyboardRow> rows = new ArrayList<>(categories);

        InlineKeyboardRow actions = new InlineKeyboardRow(List.of(
                createButton("✏\uFE0F Change name",
                        CallbackData.EDIT_CATEGORY.name()
                                + ":" + " "
                                + ":" + selectedCatId),
                createButton("❌ Delete",
                        CallbackData.DELETE_CATEGORY.name()
                                + ":" + " "
                                + ":" + selectedCatId))
        );
        rows.add(actions);
        rows.add(getReturnButton());

        String text = (selectedCatId == null)
                ? "     Choose category.     "
                : "     Now choose action.     ";

        EditMessageText message = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();

        execute(message);
    }

    @Override
    public void prepareEditCategory(Long chatId, Integer messageId, Long catId) {
        if (catId == null) {
            return;
        }

        userStates.put(chatId, new UserState(UserStateType.EDIT_NAME, catId));

        EditMessageText message = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("\uD83D\uDCDD Enter new name for the category:")
                .replyMarkup(getCancelButton())
                .build();

        execute(message);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void updateCategoryName(Long chatId, Long catId, String newName) {
        Category category = categoryService.getById(catId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        String oldName = category.getName();
        category.setName(newName);
        categoryService.save(category);

        userStates.remove(chatId);

        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(String.format("✅ Category <b>%s</b> was renamed to <b>%s</b>", oldName, newName))
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuKeyboard())
                .build());
    }

    @Transactional
    @Override
    public void deleteCategory(Long chatId, Integer messageId, Long catId) {
        Category category = categoryService.getById(catId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        activityRepository.deleteAllByCategory(category);

        String oldName = category.getName();
        categoryService.delete(category);

        EditMessageText message = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(String.format("✅ Category with the name <b>%s</b> was deleted!", oldName))
                .parseMode(ParseMode.HTML)
                .replyMarkup(getMainMenuKeyboard())
                .build();
        execute(message);
    }

    private List<InlineKeyboardRow> getDateNavigation(
            CallbackData shiftType,
            CallbackData confirmType,
            String datePattern,
            Long selectedCatId,
            LocalDate date) {

        LocalDate prev;
        LocalDate next;
        boolean isDay = shiftType.equals(CallbackData.SHIFT_DAY);
        prev = isDay ? date.minusDays(1) : date.minusMonths(1);
        next = isDay ? date.plusDays(1) : date.plusMonths(1);

        String catSuffix = (selectedCatId != null) ? ":" + selectedCatId : "";

        InlineKeyboardRow dayNavRow = new InlineKeyboardRow(List.of(
                createButton("⬅️", shiftType.name()
                        + ":" + prev + catSuffix),
                createButton(date.format(DateTimeFormatter.ofPattern(datePattern)),
                        confirmType.name() + ":" + date + catSuffix),
                createButton("➡️", shiftType.name()
                        + ":" + next + catSuffix)
        ));

        InlineKeyboardRow backRow = getReturnButton();

        return List.of(dayNavRow, backRow);
    }

    private List<InlineKeyboardRow> getCategoriesButtons(CallbackData actionType,
                                                         Long selectedCatId,
                                                         LocalDate date) {

        List<InlineKeyboardRow> categories = new ArrayList<>();

        categoryService.getAllCategories().forEach(cat -> {
            boolean isSelected = cat.getId().equals(selectedCatId);
            String buttonText = isSelected ? "✅ " + cat.getName() : cat.getName();
            String dateText = date != null ? date.toString() : " ";

            categories.add(new InlineKeyboardRow(List.of(
                    createButton(buttonText, actionType.name()
                            + ":" + dateText
                            + ":" + cat.getId())
            )));
        });

        return categories;
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private InlineKeyboardMarkup getMainMenuKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(List.of(createButton("➕ Add to category",
                                CallbackData.ADD_START.name()))),
                        new InlineKeyboardRow(List.of(createButton("📊 Report",
                                CallbackData.REPORT_START.name()))),
                        new InlineKeyboardRow(List.of(createButton("⚙\uFE0F Category settings",
                                CallbackData.SETTINGS.name())))
                ))
                .build();
    }

    private InlineKeyboardRow getReturnButton() {
        return new InlineKeyboardRow(List.of(
                createButton("⬅️ To MAIN MENU", CallbackData.BACK_TO_MAIN.name())
        ));
    }

    private InlineKeyboardMarkup getCancelButton() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(List.of(
                        createButton("❌ Cancel", CallbackData.CANCEL.name())
                )))
                .build();
    }

    private void execute(Object method) {
        try {
            if (method instanceof SendMessage sm) telegramClient.execute(sm);
            if (method instanceof EditMessageText emt) telegramClient.execute(emt);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Could not execute a command", e);
        }
    }
}
