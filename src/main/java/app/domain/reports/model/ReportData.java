package app.domain.reports.model;

import java.util.List;
import java.util.Optional;

/**
 * Собранные данные для отчёта.
 * Не хранится — пересобирается при каждом открытии/генерации.
 */
public record ReportData(
        ReportTarget target,
        String title,           // название кейса или цикла
        String subtitle,        // теги/описание
        String startedAt,       // для цикла: дата+время начала; для кейса: пусто
        String finishedAt,      // для цикла: дата+время завершения; для кейса: пусто
        String lastRunDate,     // для кейса: дата последнего прохождения
        List<ReportSection> sections
) {

    /** Находит секцию по типу, если она присутствует. */
    @SuppressWarnings("unchecked")
    public <T extends ReportSection> Optional<T> section(String type) {
        return sections.stream()
                .filter(s -> type.equals(s.sectionType()))
                .map(s -> (T) s)
                .findFirst();
    }
}
