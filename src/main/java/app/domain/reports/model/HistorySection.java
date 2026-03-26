package app.domain.reports.model;

import java.util.List;

/**
 * Детальная история прохождений.
 * Для кейса — список его прохождений по циклам.
 * Для цикла — список кейсов с их статусами в последнем (или выбранном) прохождении.
 */
public record HistorySection(
        List<HistoryRow> rows
) implements ReportSection {

    public static final String TYPE = "HISTORY";

    @Override
    public String sectionType() { return TYPE; }

    public record HistoryRow(
            int ordinal,
            String entityId,     // caseId или cycleId
            String title,        // название кейса или цикла
            String status,
            String comment,
            String dateLabel,
            String contextLabel  // цикл (для кейса) или пусто (для цикла)
    ) {}
}
