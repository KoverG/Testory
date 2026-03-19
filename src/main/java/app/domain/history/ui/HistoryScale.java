package app.domain.history.ui;

public enum HistoryScale {
    WEEK("Неделя", "week"),
    MONTH("Месяц", "month"),
    YEAR("Год", "year");

    private final String title;
    private final String settingsValue;

    HistoryScale(String title, String settingsValue) {
        this.title = title;
        this.settingsValue = settingsValue;
    }

    public String title() {
        return title;
    }

    public String settingsValue() {
        return settingsValue;
    }

    public static HistoryScale fromSettings(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONTH;
        }

        String value = raw.trim().toLowerCase();
        return switch (value) {
            case "week" -> WEEK;
            case "year" -> YEAR;
            default -> MONTH;
        };
    }
}