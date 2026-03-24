package app.domain.cycles.usecase;
/**
 * Reference to a testcase inside a cycle card.
 */
public record CycleCaseRef(String id, String titleSnapshot, String status, String comment, String statusChangedAtIso) {
    public CycleCaseRef(String id, String titleSnapshot) {
        this(id, titleSnapshot, "", "", "");
    }
    public CycleCaseRef(String id, String titleSnapshot, String status) {
        this(id, titleSnapshot, status, "", "");
    }
    public CycleCaseRef(String id, String titleSnapshot, String status, String comment) {
        this(id, titleSnapshot, status, comment, "");
    }
    public String safeId() {
        return id == null ? "" : id.trim();
    }
    public String safeTitleSnapshot() {
        return titleSnapshot == null ? "" : titleSnapshot.trim();
    }
    public String safeStatus() {
        return status == null ? "" : status.trim();
    }
    public String safeComment() {
        return comment == null ? "" : comment.trim();
    }
    public String safeStatusChangedAtIso() {
        return statusChangedAtIso == null ? "" : statusChangedAtIso.trim();
    }
}
