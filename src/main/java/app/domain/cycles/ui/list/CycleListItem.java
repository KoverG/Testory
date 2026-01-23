package app.domain.cycles.ui.list;

public record CycleListItem(String id, String title) {
    @Override public String toString() { return title; }
}
