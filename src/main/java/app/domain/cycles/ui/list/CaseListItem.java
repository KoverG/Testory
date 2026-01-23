package app.domain.cycles.ui.list;

public record CaseListItem(String id, String title) {
    @Override public String toString() { return title; }
}
