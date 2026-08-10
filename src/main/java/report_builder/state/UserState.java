package report_builder.state;

import java.time.LocalDate;

import report_builder.model.enums.CallbackData;
import report_builder.model.enums.UserStateType;

public record UserState(UserStateType type, Long targetId, LocalDate date, CallbackData operationType) {
    public static UserState empty() {
        return new UserState(UserStateType.FREE,
                null, null, null);
    }
}
