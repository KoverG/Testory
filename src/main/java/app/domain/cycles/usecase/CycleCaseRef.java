package app.domain.cycles.usecase;

/**
 * Ссылка на тесткейс внутри цикла.
 *
 * id — стабильный идентификатор для навигации.
 * titleSnapshot — снимок названия на момент добавления (для быстрого UI и как fallback).
 */
public record CycleCaseRef(String id, String titleSnapshot) {

    public String safeId() {
        return id == null ? "" : id.trim();
    }

    public String safeTitleSnapshot() {
        return titleSnapshot == null ? "" : titleSnapshot.trim();
    }
}
