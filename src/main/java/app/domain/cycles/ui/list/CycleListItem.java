package app.domain.cycles.ui.list;

public record CycleListItem(String id, String title, String createdAtUi) {
    public String safeTitle() {
        return title == null ? "" : title.trim();
    }
    public String safeCreatedAtUi() {
        return createdAtUi == null ? "" : createdAtUi.trim();
    }
}