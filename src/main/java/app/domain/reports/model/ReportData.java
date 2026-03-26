package app.domain.reports.model;

import java.util.List;
import java.util.Optional;

/**
 * Aggregated data for a generated report.
 */
public record ReportData(
        ReportTarget target,
        String title,
        String subtitle,
        String caseLabelsText,
        String caseTagsText,
        String startedAt,
        String finishedAt,
        String lastRunDate,
        ReportMetaSummary metaSummary,
        List<ReportSection> sections
) {

    @SuppressWarnings("unchecked")
    public <T extends ReportSection> Optional<T> section(String type) {
        return sections.stream()
                .filter(s -> type.equals(s.sectionType()))
                .map(s -> (T) s)
                .findFirst();
    }
}
