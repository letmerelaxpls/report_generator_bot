package report_builder.service.telegram;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
import report_builder.service.file.ReportGeneratorService;
import report_builder.state.UserState;

@Service
@RequiredArgsConstructor
public class BotServiceImpl implements BotService {
    private static final String DATE_MONTH_FORMAT = "MM.yyyy";
    private static final String DATE_FULL_FORMAT = "dd.MM.yyyy";
    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final TelegramClient telegramClient;
    private final CategoryService categoryService;
    private final ReportGeneratorService reportGeneratorService;
    private final ActivityRepository activityRepository;
    @Value("${web.app.url}")
    private String webAppUrl;

    @Override
    public void sendMainMenu(Long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text("ГОЛОВНЕ МЕНЮ")
                .replyMarkup(getMainMenuKeyboard())
                .build();
        execute(message);
    }

    @Override
    public void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();

        execute(sendMessage);
    }

    @Override
    public void editToMainMenu(Long chatId, Integer messageId) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text("ГОЛОВНЕ МЕНЮ")
                .replyMarkup(getMainMenuKeyboard())
                .build();
        execute(edit);
    }

    @Override
    public void editToCategoryMenu(Long chatId, Integer messageId, LocalDate date,
                                   Long parentId, Long selectedCatId, CallbackData operationType) {
        List<InlineKeyboardRow> rows = getCategoryButtons(parentId, selectedCatId, date, operationType);

        if (selectedCatId != null) {
            rows.addAll(getDateNavigation(CallbackData.SHIFT_DAY, operationType,
                    DATE_FULL_FORMAT, selectedCatId, date));
            rows.add(getSetAmountButton(date, selectedCatId, operationType));
        }

        String text = getParentNames(parentId) + "\n";
        rows.add(getReturnButton());

        execute(EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text + "\n" + ((selectedCatId != null)
                        ? "Підтвердіть, натиснувши на дату \n"
                        + "або обравши <b>\uD83D\uDD22 Ввести інше число</b>"
                        : "Оберіть категорію:"))
                .parseMode(ParseMode.HTML)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    @Override
    public void addToCategory(Long chatId, Integer messageId, LocalDate selectedDate, Long selectedCatId) {
        if (selectedCatId == null) {
            return;
        }

        Category category = categoryService.getById(selectedCatId)
                .orElseThrow(() -> new RuntimeException("Category with id: " + selectedCatId
                        + " was not found"));

        ActivityRecord activityRecord = activityRepository.findByCategoryAndDate(category, selectedDate)
                .orElseGet(() -> {
                    ActivityRecord newRecord = new ActivityRecord();
                    newRecord.setCategory(category);
                    newRecord.setDate(selectedDate);
                    return newRecord;
                });

        activityRecord.setCount(activityRecord.getCount() + 1);
        activityRepository.save(activityRecord);
        String location = getParentNames(selectedCatId);
        String formattedDate = selectedDate.format(DateTimeFormatter.ofPattern(DATE_FULL_FORMAT));
        EditMessageText successMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(String.format("✅ <b>%s</b> за %s. \n Було: %d \n Стало: %s",
                        location, formattedDate, activityRecord.getCount() - 1, activityRecord.getCount()))
                .parseMode(ParseMode.HTML)
                .build();

        execute(successMessage);
        sendMainMenu(chatId);
    }

    @Override
    public void subtractFromCategory(Long chatId, Integer messageId, LocalDate selectedDate, Long selectedCatId) {
        if (selectedCatId == null) {
            return;
        }

        Category category = categoryService.getById(selectedCatId)
                .orElseThrow(() -> new RuntimeException("Category with id: " + selectedCatId
                        + " was not found"));

        ActivityRecord activityRecord = activityRepository.findByCategoryAndDate(category, selectedDate)
                .orElse(null);

        String formattedDate = selectedDate.format(DateTimeFormatter.ofPattern(DATE_FULL_FORMAT));
        String text;
        String location = getParentNames(selectedCatId);
        if (activityRecord == null || activityRecord.getCount() == 0) {
            text = String.format("Записів для <b>%s</b> за %s немає",
                    location, formattedDate);
        } else {
            activityRecord.setCount(activityRecord.getCount() - 1);
            text = String.format("<b>%s</b> за %s. Було: %d. Стало: %d",
                    location, formattedDate, activityRecord.getCount() + 1, activityRecord.getCount());
            activityRepository.save(activityRecord);
        }
        EditMessageText messageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();

        execute(messageText);
        sendMainMenu(chatId);
    }

    @Override
    public void prepareCustomQuantityInput(Long chatId, Integer messageId,
                                           LocalDate date, Long catId, CallbackData operationType) {
        userStates.put(chatId, new UserState(UserStateType.AWAITING_QUANTITY_INPUT, catId, date, operationType));
        String location = getParentNames(catId);
        String operation = operationType.equals(CallbackData.ADD_TO_CATEGORY)
                ? "<b>додавання</b>"
                : "<b>віднімання</b>";
        EditMessageText message = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(location + "\n" + "\uD83D\uDD22 Введіть кількість для " + operation)
                .parseMode(ParseMode.HTML)
                .replyMarkup(getCancelButton())
                .build();
        execute(message);
    }

    @Override
    public void saveCustomQuantity(Long chatId, Long catId, LocalDate date, int quantity, CallbackData operationType) {
        Category category = categoryService.getById(catId).orElseThrow();

        ActivityRecord record = activityRepository.findByCategoryAndDate(category, date)
                .orElseGet(() -> {
                    ActivityRecord newRecord = new ActivityRecord();
                    newRecord.setCategory(category);
                    newRecord.setDate(date);
                    return newRecord;
                });
        Integer oldCount = record.getCount();
        int newCount;
        if (operationType.equals(CallbackData.ADD_TO_CATEGORY)) {
            newCount = oldCount + quantity;
        } else {
            newCount = Math.max(0, oldCount - quantity);
        }
        record.setCount(newCount);
        activityRepository.save(record);

        String location = getParentNames(catId);
        String formattedDate = date.format(DateTimeFormatter.ofPattern(DATE_FULL_FORMAT));
        clearUserState();

        sendMessage(chatId, String.format("✅ Збережено для <b>%s</b> за %s \n Було: %s \n Стало: %s",
                location, formattedDate, oldCount, newCount));
        sendMainMenu(chatId);
    }

    @Override
    public void chooseReportMonth(Long chatId, Integer messageId, LocalDate date) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>(getDateNavigation(CallbackData.SHIFT_MONTH,
                CallbackData.SEND_REPORT, DATE_MONTH_FORMAT,
                null, date));
        keyboard.add(getReturnButton());
        EditMessageText messageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text("Оберіть місяц, за який ви хочете отримати звіт.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(keyboard)
                        .build())
                .build();

        execute(messageText);
    }

    @Transactional
    @Override
    public void sendReport(Long chatId, Integer messageId, LocalDate selectedMonth) {
        LocalDate startOfMonth = selectedMonth.withDayOfMonth(1);
        LocalDate endOfMonth = selectedMonth.withDayOfMonth(selectedMonth.lengthOfMonth());

        List<ActivityRecord> records = activityRepository
                .findAllByDateBetween(startOfMonth, endOfMonth).stream()
                .sorted(Comparator.comparing(ActivityRecord::getDate))
                .toList();

        if (records.isEmpty()) {
            if (messageId != null) {
                EditMessageText message = EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text("За " + selectedMonth.format(DateTimeFormatter.ofPattern(DATE_MONTH_FORMAT))
                                + " немає записів")
                        .replyMarkup(getMainMenuKeyboard())
                        .build();
                execute(message);
            } else {
                sendMessage(chatId, "За " + selectedMonth.format(DateTimeFormatter.ofPattern(DATE_MONTH_FORMAT))
                        + " немає записів");
            }
            return;
        }

        ClassPathResource templatePath = new ClassPathResource("templates/report_template.docx");
        String outputPath = new File("reports/Report_" + chatId + "_" + selectedMonth + ".docx")
                .getAbsolutePath();

        reportGeneratorService.fillReport(templatePath, outputPath, records);
        File file = new File(outputPath);

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("LLLL",
                Locale.forLanguageTag("uk-UA"));
        String monthName = selectedMonth.format(monthFormatter);

        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId.toString())
                .document(new InputFile(file))
                .caption("✅ Ваш медичний звіт за <b>" +
                        monthName.substring(0, 1).toUpperCase() + monthName.substring(1) + "</b>")
                .parseMode(ParseMode.HTML)
                .build();

        execute(sendDocument);

        sendMainMenu(chatId);
    }

    @Override
    public UserState getUserState(Long chatId) {
        return userStates.getOrDefault(chatId, UserState.empty());
    }

    @Override
    public void clearUserState() {
        userStates.clear();
    }

    private String getParentNames(Long selectedCatId) {
        if (selectedCatId == null) {
            return "";
        }
        List<String> parentNames = new ArrayList<>();
        Category current = categoryService.getById(selectedCatId).get();

        while (current != null) {
            parentNames.add(current.getName());
            Long parentId;
            if (current.getParent() == null) {
                current = null;
            } else {
                parentId = current.getParent().getId();
                current = categoryService.getById(parentId).get();
            }
        }
        Collections.reverse(parentNames);
        if (parentNames.isEmpty()) {
            return "";
        }
        return "📍 " + String.join(" ➔ ", parentNames);
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
                        + ":" + prev + catSuffix + ":" + confirmType.name()),
                createButton(date.format(DateTimeFormatter.ofPattern(datePattern)),
                        confirmType.name() + ":" + date + catSuffix),
                createButton("➡️", shiftType.name()
                        + ":" + next + catSuffix + ":" + confirmType.name())
        ));

        return List.of(dayNavRow);
    }

    private List<InlineKeyboardRow> getCategoryButtons(Long parentId,
                                                       Long selectedCatId,
                                                       LocalDate date,
                                                       CallbackData operationType) {

        List<Category> categories = (parentId == null)
                ? categoryService.getRootCategories()
                : categoryService.getSubCategories(parentId);

        categories = categories.stream()
                .filter(c -> !c.getId().equals(1L))
                .toList();

        List<InlineKeyboardRow> rows = new ArrayList<>();

        categories.forEach((cat) -> {
            String text = cat.getName();

            if (cat.getId().equals(selectedCatId)) {
                text = "✅ " + text;
            }

            rows.add(new InlineKeyboardRow(List.of(
                    createButton(text, CallbackData.SELECTED_CAT_T0_ADD.name()
                            + ":" + date
                            + ":" + cat.getId()
                            + ":" + operationType.name())
            )));
        });
        return rows;
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
                        new InlineKeyboardRow(List.of(createButton("➕ Додати до категорії",
                                CallbackData.ADD_START.name()))),
                        new InlineKeyboardRow(List.of(createButton("➖ Відняти від категорії",
                                CallbackData.SUBTRACT_START.name()))),
                        new InlineKeyboardRow(List.of(createButton("📊 Звіт",
                                CallbackData.REPORT_START.name())))
                ))
                .build();
    }

    private InlineKeyboardRow getReturnButton() {
        return new InlineKeyboardRow(List.of(
                createButton("⬅️ До головного меню", CallbackData.BACK_TO_MAIN.name())
        ));
    }

    private InlineKeyboardRow getSetAmountButton(LocalDate date, Long selectedCatId, CallbackData operationType) {
        return new InlineKeyboardRow(List.of(createButton("\uD83D\uDD22 Ввести інше число",
                CallbackData.INPUT_CUSTOM_QUANTITY.name()
                        + ":" + date
                        + ":" + selectedCatId
                        + ":" + operationType.name())));
    }

    private InlineKeyboardMarkup getCancelButton() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(List.of(
                        createButton("❌ Відмінити", CallbackData.CANCEL.name())
                )))
                .build();
    }

    private void execute(Object method) {
        try {
            if (method instanceof SendMessage sm) telegramClient.execute(sm);
            else if (method instanceof EditMessageText emt) telegramClient.execute(emt);
            else if (method instanceof SendDocument sd) telegramClient.execute(sd);
            else if (method instanceof SetChatMenuButton scmb) telegramClient.execute(scmb);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Could not execute a command", e);
        }
    }
}
