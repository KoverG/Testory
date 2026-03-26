package app.domain.reports.export;

import app.domain.cycles.CaseStatusRegistry;
import app.domain.reports.model.HistorySection;
import app.domain.reports.model.ReportSection;

public final class HistoryRenderer implements SectionRenderer {

    @Override
    public String sectionType() { return HistorySection.TYPE; }

    @Override
    public void render(ReportSection section, StringBuilder out) {
        HistorySection s = (HistorySection) section;
        if (s.rows().isEmpty()) return;

        out.append("<section class=\"section\">\n");
        out.append("  <h2>История прохождений</h2>\n");
        out.append("  <table class=\"history-table\">\n");
        out.append("    <thead><tr>\n");
        out.append("      <th>Статус</th><th>Название / Контекст</th><th>Дата</th><th>Комментарий</th>\n");
        out.append("    </tr></thead>\n");
        out.append("    <tbody>\n");

        for (var row : s.rows()) {
            String inlineStyle = CaseStatusRegistry.htmlBadgeStyle(row.status());
            String title = row.title().isBlank() ? row.contextLabel() : row.title();
            String context = row.title().isBlank() ? "" : row.contextLabel();

            out.append("    <tr>\n");
            out.append("      <td><span class=\"badge\" style=\"").append(StatusSummaryRenderer.htmlEscape(inlineStyle)).append("\">")
               .append(StatusSummaryRenderer.htmlEscape(CaseStatusRegistry.displayLabel(row.status()))).append("</span></td>\n");
            out.append("      <td>").append(StatusSummaryRenderer.htmlEscape(title));
            if (!context.isBlank()) {
                out.append("<br><span class=\"muted\">").append(StatusSummaryRenderer.htmlEscape(context)).append("</span>");
            }
            out.append("</td>\n");
            out.append("      <td>").append(StatusSummaryRenderer.htmlEscape(row.dateLabel())).append("</td>\n");
            out.append("      <td>").append(StatusSummaryRenderer.htmlEscape(row.comment())).append("</td>\n");
            out.append("    </tr>\n");
        }

        out.append("    </tbody>\n");
        out.append("  </table>\n");
        out.append("</section>\n");
    }
}