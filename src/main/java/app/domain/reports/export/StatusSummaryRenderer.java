package app.domain.reports.export;

import app.domain.cycles.CaseStatusRegistry;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.StatusSummarySection;

import java.util.Locale;
import java.util.Map;

public final class StatusSummaryRenderer implements SectionRenderer {

    @Override
    public String sectionType() { return StatusSummarySection.TYPE; }

    @Override
    public void render(ReportSection section, StringBuilder out) {
        StatusSummarySection s = (StatusSummarySection) section;

        out.append("<section class=\"section\">\n");
        out.append("  <h2>Сводка по статусам</h2>\n");
        out.append("  <div class=\"summary-layout\">\n");
        out.append("    <div class=\"summary-donut-wrap\">\n");
        out.append("      <div class=\"summary-donut\" style=\"").append(buildDonutStyle(s)).append("\">\n");
        out.append("        <div class=\"summary-donut-hole\">\n");
        out.append("          <span class=\"summary-donut-total\">").append(s.total()).append("</span>\n");
        out.append("          <span class=\"summary-donut-label\">всего кейсов</span>\n");
        out.append("        </div>\n");
        out.append("      </div>\n");
        out.append("    </div>\n");
        out.append("    <div class=\"summary-legend\">\n");

        for (Map.Entry<String, Integer> entry : s.countsByStatus().entrySet()) {
            int count = entry.getValue();
            if (count == 0) continue;
            double pct = s.total() > 0 ? (count * 100.0 / s.total()) : 0;
            String inlineStyle = CaseStatusRegistry.htmlBadgeStyle(entry.getKey());
            out.append("      <div class=\"summary-row\">\n");
            out.append("        <span class=\"badge summary-badge\" style=\"").append(htmlEscape(inlineStyle)).append("\">")
                    .append(htmlEscape(CaseStatusRegistry.displayLabel(entry.getKey()))).append("</span>\n");
            out.append("        <span class=\"summary-metrics\"><strong>").append(count).append("</strong><span class=\"summary-pct\">")
                    .append(String.format(Locale.US, "%.0f%%", pct)).append("</span></span>\n");
            out.append("      </div>\n");
        }

        out.append("    </div>\n");
        out.append("  </div>\n");
        out.append("</section>\n");
    }

    private static String buildDonutStyle(StatusSummarySection section) {
        if (section.total() <= 0) {
            return "background: conic-gradient(#dfe4ea 0deg 360deg);";
        }

        StringBuilder gradient = new StringBuilder("background: conic-gradient(");
        double start = 0d;
        boolean first = true;
        for (Map.Entry<String, Integer> entry : section.countsByStatus().entrySet()) {
            int count = entry.getValue();
            if (count <= 0) continue;
            double sweep = count * 360.0 / section.total();
            double end = Math.min(360d, start + sweep);
            if (!first) {
                gradient.append(", ");
            }
            gradient.append(CaseStatusRegistry.color(entry.getKey()))
                    .append(" ")
                    .append(String.format(Locale.US, "%.2fdeg %.2fdeg", start, end));
            start = end;
            first = false;
        }
        if (start < 360d) {
            if (!first) {
                gradient.append(", ");
            }
            gradient.append("#dfe4ea ")
                    .append(String.format(Locale.US, "%.2fdeg 360deg", start));
        }
        gradient.append(");");
        return gradient.toString();
    }

    static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
