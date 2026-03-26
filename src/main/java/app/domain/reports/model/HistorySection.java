package app.domain.reports.model;

import java.util.List;

public record HistorySection(
        List<HistoryRow> rows
) implements ReportSection {

    public static final String TYPE = "HISTORY";

    @Override
    public String sectionType() { return TYPE; }

    public record HistoryRow(
            int ordinal,
            String entityId,
            String title,
            String status,
            String comment,
            String dateLabel,
            String contextLabel,
            String taskUrl
    ) {}
}