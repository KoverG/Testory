package app.domain.history.ui;

public enum HistoryScale {
    DAY("День"),
    WEEK("Неделя"),
    MONTH("Месяц"),
    YEAR("Год");

    private final String title;

    HistoryScale(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}