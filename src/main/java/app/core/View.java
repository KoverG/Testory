package app.core;

public enum View {
    HOME("/ui/home.fxml", "view.home"),

    TEST_CASES("/ui/testcases.fxml", "view.testCases"),
    CYCLES("/ui/cycles.fxml", "view.cycles"),
    HISTORY("/ui/history.fxml", "view.history"),
    ANALYTICS("/ui/analytics.fxml", "view.analytics"),
    REPORTS("/ui/reports.fxml", "view.reports");

    private final String fxml;
    private final String titleKey;

    View(String fxml, String titleKey) {
        this.fxml = fxml;
        this.titleKey = titleKey;
    }

    public String fxml() { return fxml; }

    public String title() {
        return I18n.t(titleKey);
    }

    public String titleKey() { return titleKey; }
}
