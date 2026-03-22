package app.domain.reports.export;

import app.domain.reports.model.ReportSection;
import app.domain.reports.model.StatusSummarySection;

import java.util.Map;

public final class StatusSummaryRenderer implements SectionRenderer {

    @Override
    public String sectionType() { return StatusSummarySection.TYPE; }

    @Override
    public void render(ReportSection section, StringBuilder out) {
        StatusSummarySection s = (StatusSummarySection) section;

        out.append("<section class=\"section\">\n");
        out.append("  <h2>Сводка по статусам</h2>\n");
        out.append("  <p class=\"total\">Всего прохождений: <strong>").append(s.total()).append("</strong></p>\n");
        out.append("  <table class=\"summary-table\">\n");
        out.append("    <thead><tr><th>Статус</th><th>Количество</th><th>%</th></tr></thead>\n");
        out.append("    <tbody>\n");

        for (Map.Entry<String, Integer> entry : s.countsByStatus().entrySet()) {
            int count = entry.getValue();
            if (count == 0) continue;
            double pct = s.total() > 0 ? (count * 100.0 / s.total()) : 0;
            String cssClass = statusCss(entry.getKey());
            out.append("    <tr>\n");
            out.append("      <td><span class=\"badge ").append(cssClass).append("\">")
               .append(htmlEscape(entry.getKey())).append("</span></td>\n");
            out.append("      <td>").append(count).append("</td>\n");
            out.append("      <td>").append(String.format("%.0f%%", pct)).append("</td>\n");
            out.append("    </tr>\n");
        }

        out.append("    </tbody>\n");
        out.append("  </table>\n");
        out.append("</section>\n");
    }

    static String statusCss(String status) {
        if (status == null) return "status-unknown";
        return switch (status) {
            case "PASSED"           -> "status-passed";
            case "PASSED_WITH_BUGS" -> "status-bugs";
            case "FAILED"           -> "status-failed";
            case "CRITICAL_FAILED"  -> "status-critical";
            case "SKIPPED"          -> "status-skipped";
            case "IN_PROGRESS"      -> "status-progress";
            default                 -> "status-unknown";
        };
    }

    static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
