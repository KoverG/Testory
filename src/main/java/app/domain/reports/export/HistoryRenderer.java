package app.domain.reports.export;

import app.domain.cycles.CaseStatusDefinition;
import app.domain.cycles.CaseStatusRegistry;
import app.domain.reports.model.HistorySection;
import app.domain.reports.model.ReportSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HistoryRenderer implements SectionRenderer {

    private static final String FILTER_ALL = "__ALL__";
    private static final String FILTER_NOT_STARTED = "__NOT_STARTED__";

    @Override
    public String sectionType() { return HistorySection.TYPE; }

    @Override
    public void render(ReportSection section, StringBuilder out) {
        HistorySection s = (HistorySection) section;
        if (s.rows().isEmpty()) return;

        String filterId = "history-filter-" + Integer.toHexString(System.identityHashCode(s));
        out.append("<section class=\"section history-section\" data-history-section>\n");
        out.append("  <h2>История прохождений</h2>\n");
        out.append("  <div class=\"history-filter-group\" data-filter-group=\"").append(filterId).append("\">\n");
        appendFilterChip(out, "Все", FILTER_ALL, true);
        for (String status : orderedStatuses(historyStatusCounts(s))) {
            if (historyHasStatus(s, status)) {
                appendFilterChip(out, CaseStatusRegistry.displayLabel(status), status, false);
            }
        }
        if (historyHasNotStarted(s)) {
            appendFilterChip(out, "Не начат", FILTER_NOT_STARTED, false);
        }
        out.append("  </div>\n");
        out.append("  <p class=\"history-empty-filtered\" hidden>Нет кейсов по выбранному статусу</p>\n");
        out.append("  <table class=\"history-table\">\n");
        out.append("    <thead><tr>\n");
        out.append("      <th>№</th><th>Статус</th><th>Имя кейса</th><th>Комментарий</th><th>Дата</th>\n");
        out.append("    </tr></thead>\n");
        out.append("    <tbody>\n");

        for (var row : s.rows()) {
            String inlineStyle = CaseStatusRegistry.htmlBadgeStyle(row.status());
            String title = row.title().isBlank() ? row.contextLabel() : row.title();
            String status = normalizedStatus(row.status());

            out.append("    <tr class=\"history-row\" data-history-row data-status=\"")
                    .append(StatusSummaryRenderer.htmlEscape(status))
                    .append("\">\n");
            out.append("      <td>").append(formatOrdinal(row.ordinal())).append("</td>\n");
            out.append("      <td><span class=\"badge\" style=\"").append(StatusSummaryRenderer.htmlEscape(inlineStyle)).append("\">")
                    .append(StatusSummaryRenderer.htmlEscape(CaseStatusRegistry.displayLabel(row.status()))).append("</span></td>\n");
            out.append("      <td>").append(StatusSummaryRenderer.htmlEscape(title)).append("</td>\n");
            out.append("      <td>").append(StatusSummaryRenderer.htmlEscape(row.comment())).append("</td>\n");
            out.append("      <td>").append(StatusSummaryRenderer.htmlEscape(row.dateLabel())).append("</td>\n");
            out.append("    </tr>\n");
        }

        out.append("    </tbody>\n");
        out.append("  </table>\n");
        out.append("</section>\n");
    }

    private static void appendFilterChip(StringBuilder out, String label, String value, boolean active) {
        out.append("    <button type=\"button\" class=\"history-filter-chip");
        if (active) {
            out.append(" history-filter-chip-active");
        }
        out.append("\" data-history-filter=\"").append(StatusSummaryRenderer.htmlEscape(value)).append("\">")
                .append(StatusSummaryRenderer.htmlEscape(label))
                .append("</button>\n");
    }

    private static List<String> orderedStatuses(Map<String, Integer> countsByStatus) {
        List<String> ordered = new ArrayList<>();
        for (CaseStatusDefinition definition : CaseStatusRegistry.orderedWith(countsByStatus)) {
            ordered.add(definition.code());
        }
        return ordered;
    }

    private static Map<String, Integer> historyStatusCounts(HistorySection section) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (HistorySection.HistoryRow row : section.rows()) {
            String status = normalizedStatus(row.status());
            if (status.isBlank()) {
                continue;
            }
            counts.merge(status, 1, Integer::sum);
        }
        return counts;
    }

    private static boolean historyHasStatus(HistorySection section, String status) {
        return section.rows().stream().anyMatch(row -> status.equals(normalizedStatus(row.status())));
    }

    private static boolean historyHasNotStarted(HistorySection section) {
        return section.rows().stream().anyMatch(row -> normalizedStatus(row.status()).isBlank());
    }

    private static String normalizedStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private static String formatOrdinal(int ordinal) {
        return ordinal > 0 ? Integer.toString(ordinal) : "—";
    }
}
