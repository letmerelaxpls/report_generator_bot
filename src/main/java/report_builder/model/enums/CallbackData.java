package report_builder.model.enums;

public enum CallbackData {
    ADD_START,
    SUBTRACT_START,
    REPORT_START,
    BACK_TO_MAIN,
    SHIFT_DAY,
    SHIFT_MONTH,
    SEND_REPORT,
    ADD_TO_CATEGORY,
    SUBTRACT_FROM_CATEGORY,
    CANCEL,
    SELECTED_CAT_T0_ADD,
    INPUT_CUSTOM_QUANTITY;

    public static CallbackData fromString(String data) {
        return CallbackData.valueOf(data);
    }
}
