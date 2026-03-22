package app.domain.reports.model;

import java.util.Map;

/**
 * Сводка по статусам: сколько прохождений с каждым статусом.
 */
public record StatusSummarySection(
        int total,
        Map<String, Integer> countsByStatus  // статус → количество
) implements ReportSection {

    public static final String TYPE = "STATUS_SUMMARY";

    @Override
    public String sectionType() { return TYPE; }
}
