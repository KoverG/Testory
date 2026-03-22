package app.domain.reports.export;

import app.domain.reports.model.ReportSection;
import app.domain.reports.model.TrendSection;
import app.domain.reports.model.TrendSection.TrendCapsule;

public final class TrendRenderer implements SectionRenderer {

    @Override
    public String sectionType() { return TrendSection.TYPE; }

    @Override
    public void render(ReportSection section, StringBuilder out) {
        TrendSection s = (TrendSection) section;
        if (s.capsules().isEmpty()) return;

        out.append("<section class=\"section\">\n");
        out.append("  <h2>Тренд</h2>\n");
        out.append("  <div class=\"trend\">\n");

        for (TrendCapsule cap : s.capsules()) {
            String css = capsuleCss(cap.colorKey());
            String shape = cap.wide() ? "capsule-wide" : "capsule-round";
            String label = capsuleLabel(cap.colorKey());
            out.append("    <div class=\"capsule ").append(css).append(" ").append(shape)
               .append("\" title=\"").append(StatusSummaryRenderer.htmlEscape(label)).append("\">")
               .append(cap.count()).append("</div>\n");
        }

        out.append("  </div>\n");
        out.append("</section>\n");
    }

    private static String capsuleCss(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "cap-passed";
            case "bugs"   -> "cap-bugs";
            case "failed" -> "cap-failed";
            default       -> "cap-unknown";
        };
    }

    private static String capsuleLabel(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "Passed";
            case "bugs"   -> "Passed with bugs";
            case "failed" -> "Failed";
            default       -> colorKey;
        };
    }
}
