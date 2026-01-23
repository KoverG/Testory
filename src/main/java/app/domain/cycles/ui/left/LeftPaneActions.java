package app.domain.cycles.ui.left;

public interface LeftPaneActions {
    void onCreate();
    void onDelete();
    void onFilter();
    void onSort();
    void onSearch(String q);
    void onOpenSelected();
}
