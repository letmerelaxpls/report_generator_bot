package report_builder.state;

import report_builder.model.enums.UserStateType;

public record UserState(UserStateType type, Long targetId) {
    public static UserState empty() {
        return new UserState(UserStateType.FREE, null);
    }
}
