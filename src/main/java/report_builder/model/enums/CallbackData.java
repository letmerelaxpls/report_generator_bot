package report_builder.model.enums;

public enum CallbackData {
    ADD_START,
    ADD_CATEGORY_START,
    REPORT_START,
    BACK_TO_MAIN,
    SHIFT_DAY,
    SHIFT_MONTH,
    SEND_REPORT,
    ADD_TO_CATEGORY,
    CANCEL,
    SELECTED_CAT_T0_ADD,
    SELECTED_CAT_TO_MANAGE,
    SETTINGS,
    MANAGE_CATEGORIES,
    EDIT_CATEGORY,
    DELETE_CATEGORY,
    CONFIRM_DELETE;

    public static CallbackData fromString(String data) {
        return CallbackData.valueOf(data);
    }
}
