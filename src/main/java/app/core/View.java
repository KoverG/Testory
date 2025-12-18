package app.core;

public enum View {
    HOME("/ui/home.fxml", "Home"),

    TEST_CASES("/ui/testcases.fxml", "Test Cases"),
    CYCLES("/ui/cycles.fxml", "Cycles"),
    HISTORY("/ui/history.fxml", "History"),
    ANALYTICS("/ui/analytics.fxml", "Analytics"),
    REPORTS("/ui/reports.fxml", "Reports");

    private final String fxml;
    private final String title;

    View(String fxml, String title) {
        this.fxml = fxml;
        this.title = title;
    }

    public String fxml() { return fxml; }
    public String title() { return title; }
}
