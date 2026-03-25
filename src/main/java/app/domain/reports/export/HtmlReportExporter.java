package app.domain.reports.export;

import app.domain.reports.model.HistorySection;
import app.domain.reports.model.ReportData;
import app.domain.reports.model.ReportMetaSummary;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.StatusSummarySection;
import app.domain.reports.model.TrendSection;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class HtmlReportExporter {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Map<String, SectionRenderer> renderers;

    public HtmlReportExporter() {
        renderers = buildRenderers();
    }

    private static Map<String, SectionRenderer> buildRenderers() {
        Map<String, SectionRenderer> m = new LinkedHashMap<>();
        register(m, new StatusSummaryRenderer());
        register(m, new TrendRenderer());
        register(m, new HistoryRenderer());
        return m;
    }

    private static void register(Map<String, SectionRenderer> m, SectionRenderer r) {
        m.put(r.sectionType(), r);
    }

    public void export(ReportData data, String recommendation, Consumer<String> onError) {
        try {
            String html = buildHtml(data, recommendation);
            String ts = LocalDateTime.now().format(TS_FMT);
            String safeName = "testory_report_" + sanitize(data.target().id()) + "_" + ts + ".html";
            Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
            Path file = tmpDir.resolve(safeName);
            Files.writeString(file, html, StandardCharsets.UTF_8);
            openInBrowser(file);
        } catch (Exception e) {
            if (onError != null) onError.accept("Ошибка генерации отчёта: " + e.getMessage());
        }
    }

    private String buildHtml(ReportData data, String recommendation) {
        StringBuilder sb = new StringBuilder(8192);

        String genDate = LocalDateTime.now().format(DATE_FMT);
        String typeLabel = switch (data.target().type()) {
            case TEST_CASE -> "Тест-кейс";
            case CYCLE -> "Цикл";
        };

        sb.append("<!DOCTYPE html>\n<html lang=\"ru\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>Отчёт: ").append(esc(data.title())).append("</title>\n");
        sb.append("<style>\n").append(CSS).append("</style>\n");
        sb.append("</head>\n<body>\n");

        sb.append("<header>\n");
        sb.append("  <div class=\"header-meta\">Testory - ").append(esc(typeLabel)).append("</div>\n");
        sb.append("  <h1>").append(esc(data.title())).append("</h1>\n");

        if (data.metaSummary() != null) {
            renderMetaSummary(data.metaSummary(), data.<TrendSection>section(TrendSection.TYPE).orElse(null), recommendation, sb);
        } else {
            if (!data.subtitle().isBlank()) {
                sb.append("  <p class=\"subtitle\">").append(esc(data.subtitle())).append("</p>\n");
            }
            if (!data.lastRunDate().isBlank()) {
                sb.append("  <p class=\"meta-line\">Последнее прохождение: <strong>")
                        .append(esc(data.lastRunDate())).append("</strong></p>\n");
            }
        }

        sb.append("  <p class=\"meta-line gen-date\">Сгенерировано: ").append(esc(genDate)).append("</p>\n");
        sb.append("</header>\n");

        for (ReportSection section : data.sections()) {
            if (TrendSection.TYPE.equals(section.sectionType())) {
                continue;
            }
            SectionRenderer renderer = renderers.get(section.sectionType());
            if (renderer != null) renderer.render(section, sb);
        }

        sb.append("<footer>Testory - система управления тестированием</footer>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void renderMetaSummary(ReportMetaSummary meta, TrendSection trend, String recommendation, StringBuilder sb) {
        sb.append("  <div class=\"meta-surface\">\n");

        if (meta.hasContext()) {
            sb.append("    <div class=\"chip-row\">\n");
            appendChip(sb, meta.category(), "chip");
            appendChip(sb, meta.environment(), "chip");
            appendTaskChip(sb, "Задача: " + meta.taskLabel(), meta.taskUrl());
            sb.append("    </div>\n");
        }

        appendMetricRow(sb,
                new HtmlMetric("Начат", meta.startedAt(), false, ""),
                new HtmlMetric(meta.lifecycleLabel(), meta.lifecycleValue(), false, ""),
                new HtmlMetric("Длительность", meta.duration(), true, meta.durationFull())
        );

        sb.append("    <div class=\"metric-row metric-row-progress\">\n");
        appendMetricSequence(sb,
                new HtmlMetric("Всего кейсов", String.valueOf(meta.totalCases()), true, ""),
                new HtmlMetric("Пройдено кейсов", String.valueOf(meta.completedCases()), true, ""),
                new HtmlMetric("Прогресс", meta.completionPercent() + "%", true, "")
        );
        if (trend != null && !trend.capsules().isEmpty()) {
            sb.append("      <span class=\"metric-spacer\"></span>\n");
            sb.append("      <span class=\"capsule-strip\">");
            for (TrendSection.TrendCapsule cap : trend.capsules()) {
                sb.append("<span class=\"capsule ")
                        .append(cap.wide() ? "capsule-wide " : "capsule-round ")
                        .append(capsuleCss(cap.colorKey()))
                        .append("\" title=\"")
                        .append(esc(capsuleLabel(cap.colorKey()) + ": " + cap.count()))
                        .append("\">")
                        .append(cap.count())
                        .append("</span>");
            }
            sb.append("</span>\n");
        }
        sb.append("    </div>\n");

        if (recommendation != null && !recommendation.isBlank()) {
            sb.append("    <div class=\"metric-row\">\n");
            appendChip(sb, recommendationLabel(recommendation), "chip chip-recommend " + recommendationCss(recommendation));
            sb.append("    </div>\n");
        }

        sb.append("  </div>\n");
    }

    private static void appendMetricRow(StringBuilder sb, HtmlMetric... metrics) {
        sb.append("    <div class=\"metric-row\">\n");
        appendMetricSequence(sb, metrics);
        sb.append("    </div>\n");
    }

    private static void appendMetricSequence(StringBuilder sb, HtmlMetric... metrics) {
        boolean first = true;
        for (HtmlMetric metric : metrics) {
            if (metric == null || metric.isEmpty()) continue;
            if (!first) {
                sb.append("      <span class=\"metric-separator\">|</span>\n");
            }
            appendMetric(sb, metric.label(), metric.value(), metric.strongValue(), metric.tooltipText());
            first = false;
        }
    }

    private static void appendChip(StringBuilder sb, String text, String cssClass) {
        if (text == null || text.isBlank()) return;
        sb.append("      <span class=\"").append(cssClass).append("\">")
                .append(esc(text))
                .append("</span>\n");
    }

    private static void appendTaskChip(StringBuilder sb, String text, String url) {
        if (text == null || text.isBlank()) return;
        if (url == null || url.isBlank()) {
            appendChip(sb, text, "chip");
            return;
        }
        sb.append("      <a class=\"chip chip-link\" href=\"")
                .append(esc(url))
                .append("\" title=\"")
                .append(esc(url))
                .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                .append(esc(text))
                .append("</a>\n");
    }

    private static void appendMetric(StringBuilder sb, String label, String value, boolean strongValue, String tooltipText) {
        boolean hasLabel = label != null && !label.isBlank();
        boolean hasValue = value != null && !value.isBlank();
        if (!hasLabel && !hasValue) return;

        sb.append("      <span class=\"metric-item\">");
        if (hasLabel) {
            sb.append("<span class=\"metric-label\">").append(esc(label)).append("</span>");
        }
        if (hasValue) {
            sb.append("<span class=\"metric-value")
                    .append(strongValue ? " metric-value-strong" : "")
                    .append("\"");
            if (tooltipText != null && !tooltipText.isBlank()) {
                sb.append(" title=\"").append(esc(tooltipText)).append("\"");
            }
            sb.append(">")
                    .append(esc(value))
                    .append("</span>");
        }
        sb.append("</span>\n");
    }

    private static String capsuleCss(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "cap-passed";
            case "bugs" -> "cap-bugs";
            case "failed" -> "cap-failed";
            default -> "cap-unknown";
        };
    }

    private static String capsuleLabel(String colorKey) {
        return switch (colorKey == null ? "" : colorKey) {
            case "passed" -> "Passed";
            case "bugs" -> "With bugs";
            case "failed" -> "Failed";
            default -> colorKey;
        };
    }

    private static String recommendationLabel(String recommendation) {
        return switch (recommendation) {
            case "recommended" -> "Рекомендован";
            case "needs_work" -> "Требует доработки";
            case "not_recommended" -> "Не рекомендован";
            default -> recommendation;
        };
    }

    private static String recommendationCss(String recommendation) {
        return switch (recommendation) {
            case "recommended" -> "recommend-good";
            case "needs_work" -> "recommend-warn";
            case "not_recommended" -> "recommend-bad";
            default -> "";
        };
    }

    private static void openInBrowser(Path file) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(file.toUri());
                return;
            }
        }
        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", file.toAbsolutePath().toString()});
    }

    private static String sanitize(String id) {
        if (id == null) return "unknown";
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    static String esc(String s) {
        return StatusSummaryRenderer.htmlEscape(s);
    }

    private static final String CSS =
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 14px; color: #1a1a2e; background: #f5f5f7; padding: 24px; }\n" +
            "header { background: #fff; border-radius: 12px; padding: 24px 28px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }\n" +
            ".header-meta { font-size: 11px; color: #888; text-transform: uppercase; letter-spacing: .06em; margin-bottom: 6px; }\n" +
            "h1 { font-size: 22px; font-weight: 700; margin-bottom: 8px; }\n" +
            ".subtitle { color: #555; margin-bottom: 4px; }\n" +
            ".meta-line { font-size: 12px; color: #888; margin-top: 6px; }\n" +
            ".meta-surface { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; padding: 14px 16px; border: 1px solid rgba(26,26,46,0.08); border-radius: 14px; background: linear-gradient(180deg, rgba(255,255,255,0.82), rgba(248,248,251,0.96)); }\n" +
            ".chip-row, .metric-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }\n" +
            ".metric-row-progress { width: 100%; }\n" +
            ".metric-spacer { flex: 1 1 auto; }\n" +
            ".chip { display: inline-flex; align-items: center; min-height: 28px; padding: 0 12px; border-radius: 999px; border: 1px solid rgba(26,26,46,0.08); background: rgba(26,26,46,0.04); color: #3c4357; font-size: 12px; font-weight: 700; text-decoration: none; }\n" +
            ".chip-link { cursor: pointer; }\n" +
            ".chip-link:hover { background: rgba(26,26,46,0.08); }\n" +
            ".chip-recommend { border-width: 1.5px; }\n" +
            ".recommend-good { background: rgba(39,174,96,0.12); color: #1f6f46; border-color: rgba(39,174,96,0.28); }\n" +
            ".recommend-warn { background: rgba(243,156,18,0.14); color: #995f00; border-color: rgba(243,156,18,0.34); }\n" +
            ".recommend-bad { background: rgba(231,76,60,0.12); color: #a03022; border-color: rgba(231,76,60,0.3); }\n" +
            ".metric-item { display: inline-flex; align-items: center; gap: 6px; min-height: 24px; }\n" +
            ".metric-label { color: #7a7f8e; font-size: 12px; font-weight: 600; }\n" +
            ".metric-value { color: #212736; font-size: 12px; font-weight: 700; }\n" +
            ".metric-value-strong { font-size: 13px; }\n" +
            ".metric-separator { color: #9aa1af; font-size: 12px; font-weight: 700; }\n" +
            ".capsule-strip { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }\n" +
            ".section { background: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }\n" +
            ".section h2 { font-size: 15px; font-weight: 600; margin-bottom: 14px; color: #333; }\n" +
            ".total { margin-bottom: 10px; color: #555; }\n" +
            "table { border-collapse: collapse; width: 100%; }\n" +
            "th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #f0f0f0; }\n" +
            "th { font-weight: 600; color: #666; font-size: 12px; }\n" +
            ".trend { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 6px; }\n" +
            ".capsule { display: inline-flex; align-items: center; justify-content: center; height: 30px; border-radius: 999px; font-size: 12px; font-weight: 800; color: #fff; }\n" +
            ".capsule-wide { padding: 0 16px; min-width: 60px; }\n" +
            ".capsule-round { width: 30px; }\n" +
            ".cap-passed { background: #27ae60; }\n" +
            ".cap-bugs { background: #f39c12; }\n" +
            ".cap-failed { background: #e74c3c; }\n" +
            ".cap-unknown { background: #999; }\n" +
            ".muted { font-size: 11px; color: #aaa; }\n" +
            ".badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; }\n" +
            ".status-passed { background: #d4f7dc; color: #1a7a35; }\n" +
            ".status-bugs { background: #fff3cd; color: #856404; }\n" +
            ".status-failed { background: #fde8e8; color: #c0392b; }\n" +
            ".status-critical { background: #f5c6cb; color: #721c24; }\n" +
            ".status-skipped { background: #e2e3e5; color: #495057; }\n" +
            ".status-progress { background: #cce5ff; color: #004085; }\n" +
            ".status-unknown { background: #f0f0f0; color: #888; }\n" +
            "footer { text-align: center; font-size: 11px; color: #bbb; margin-top: 24px; padding-top: 12px; }\n";

    private record HtmlMetric(String label, String value, boolean strongValue, String tooltipText) {
        boolean isEmpty() {
            return (label == null || label.isBlank()) && (value == null || value.isBlank());
        }
    }
}
