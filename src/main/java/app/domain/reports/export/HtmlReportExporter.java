package app.domain.reports.export;

import app.domain.cycles.CaseStatusRegistry;
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
        sb.append("</head>\n<body data-theme=\"light\">\n");

        sb.append("<header>\n");
        sb.append("  <div class=\"header-topline\">\n");
        appendTopMeta(sb, "Сгенерировано", genDate);
        appendTopMeta(sb, "Тип", typeLabel);
        if (data.target().type() == app.domain.reports.model.ReportTargetType.CYCLE) {
            appendRecommendationMeta(sb, recommendation);
        }
        sb.append("    <button type=\"button\" class=\"theme-toggle\" data-theme-toggle aria-label=\"Переключить тему\"><span class=\"theme-toggle-light\">Светлая</span><span class=\"theme-toggle-divider\">/</span><span class=\"theme-toggle-dark\">Темная</span></button>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"header-title-row\">");
        appendIconLink(sb, data.caseTaskUrl(), "Открыть задачу", "header-task-link");
        sb.append("<h1>").append(esc(data.title())).append("</h1>");
        sb.append("</div>\n");

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

        sb.append("</header>\n");

        for (ReportSection section : data.sections()) {
            if (TrendSection.TYPE.equals(section.sectionType())) {
                continue;
            }
            SectionRenderer renderer = renderers.get(section.sectionType());
            if (renderer != null) renderer.render(section, sb);
        }

        sb.append("<footer>Testory - система управления тестированием</footer>\n");
        sb.append("<script>\n").append(JS).append("</script>\n");
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

    static void appendIconLink(StringBuilder sb, String url, String ariaLabel, String cssClass) {
        if (url == null || url.isBlank()) return;
        sb.append("<a class=\"")
                .append(cssClass)
                .append("\" href=\"")
                .append(esc(url))
                .append("\" title=\"")
                .append(esc(url))
                .append("\" aria-label=\"")
                .append(esc(ariaLabel))
                .append("\" target=\"_blank\" rel=\"noopener noreferrer\">&#128279;</a>");
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

    private static void appendTopMeta(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append("    <span class=\"top-meta\">");
        sb.append("<span class=\"top-meta-label\">").append(esc(label)).append(":</span>");
        sb.append("<span class=\"top-meta-value\">").append(esc(value)).append("</span>");
        sb.append("</span>\n");
    }

    private static void appendRecommendationMeta(StringBuilder sb, String recommendation) {
        String label = recommendationLabel(recommendation);
        String css = recommendationCss(recommendation);
        sb.append("    <span class=\"top-meta top-meta-recommend chip chip-recommend");
        if (!css.isBlank()) {
            sb.append(" ").append(css);
        }
        sb.append("\">");
        sb.append("<span class=\"top-meta-label\">Решение:</span>");
        sb.append("<span class=\"top-meta-value\">").append(esc(label)).append("</span>");
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
            case "passed" -> CaseStatusRegistry.displayLabel(CaseStatusRegistry.PASSED);
            case "bugs" -> CaseStatusRegistry.displayLabel(CaseStatusRegistry.PASSED_WITH_BUGS);
            case "failed" -> CaseStatusRegistry.displayLabel(CaseStatusRegistry.FAILED);
            default -> colorKey;
        };
    }

    private static String recommendationLabel(String recommendation) {
        if (recommendation == null || recommendation.isBlank()) {
            return "Без решения";
        }
        return switch (recommendation) {
            case "recommended" -> "Рекомендован";
            case "needs_work" -> "Требует доработки";
            case "not_recommended" -> "Не рекомендован";
            default -> recommendation;
        };
    }

    private static String recommendationCss(String recommendation) {
        if (recommendation == null || recommendation.isBlank()) {
            return "recommend-none";
        }
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
            ":root { --bg: #f5f5f7; --surface: #ffffff; --surface-soft: linear-gradient(180deg, rgba(255,255,255,0.82), rgba(248,248,251,0.96)); --surface-muted: rgba(26,26,46,0.04); --surface-muted-hover: rgba(26,26,46,0.08); --border: rgba(26,26,46,0.08); --border-strong: rgba(26,26,46,0.16); --text: #212736; --text-soft: #3c4357; --muted: #7a7f8e; --shadow: 0 1px 4px rgba(0,0,0,.08); --table-border: #f0f0f0; --hole: #ffffff; }\n" +
            "body[data-theme='dark'] { --bg: #11161d; --surface: #171d26; --surface-soft: linear-gradient(180deg, rgba(31,39,50,0.94), rgba(22,28,37,0.98)); --surface-muted: rgba(255,255,255,0.08); --surface-muted-hover: rgba(255,255,255,0.14); --border: rgba(255,255,255,0.12); --border-strong: rgba(255,255,255,0.24); --text: #eef3fb; --text-soft: #d7dfeb; --muted: #97a3b7; --shadow: 0 10px 30px rgba(0,0,0,.28); --table-border: rgba(255,255,255,0.08); --hole: #171d26; }\n" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 14px; color: var(--text); background: var(--bg); padding: 24px; transition: background-color .2s ease, color .2s ease; }\n" +
            "header { background: var(--surface); border-radius: 12px; padding: 24px 28px; margin-bottom: 16px; box-shadow: var(--shadow); transition: background-color .2s ease, box-shadow .2s ease; }\n" +
            ".header-topline { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 12px; }\n" +
            ".top-meta { display: inline-flex; align-items: center; gap: 6px; min-height: 28px; padding: 0 12px; border-radius: 999px; background: var(--surface-muted); border: 1px solid var(--border); color: var(--text-soft); font-size: 12px; font-weight: 700; transition: background-color .2s ease, border-color .2s ease, color .2s ease; }\n" +
            ".top-meta-label { color: var(--muted); font-weight: 600; }\n" +
            ".top-meta-value { color: var(--text); font-weight: 800; }\n" +
            ".top-meta-recommend { margin-left: auto; }\n" +
            ".theme-toggle { margin-left: auto; appearance: none; border: 1px solid var(--border); background: var(--surface-muted); color: var(--text-soft); border-radius: 999px; padding: 6px 12px; font-size: 12px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; transition: background-color .2s ease, border-color .2s ease, color .2s ease; }\n" +
            ".theme-toggle:hover { background: var(--surface-muted-hover); }\n" +
            ".theme-toggle-light, .theme-toggle-dark { opacity: .55; }\n" +
            "body[data-theme='light'] .theme-toggle-light, body[data-theme='dark'] .theme-toggle-dark { opacity: 1; color: var(--text); }\n" +
            ".theme-toggle-divider { color: var(--muted); }\n" +
            ".header-title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }\n" +
            "h1 { font-size: 22px; font-weight: 700; margin: 0; color: var(--text); }\n" +
            ".header-task-link { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 8px; border: 1px solid var(--border); background: var(--surface-muted); color: var(--text-soft); text-decoration: none; font-size: 15px; line-height: 1; transition: background-color .2s ease, border-color .2s ease, color .2s ease; }\n" +
            ".header-task-link:hover { background: var(--surface-muted-hover); color: var(--text); }\n" +
            ".subtitle { color: var(--text-soft); margin-bottom: 4px; }\n" +
            ".meta-line { font-size: 12px; color: var(--muted); margin-top: 6px; }\n" +
            ".meta-surface { display: flex; flex-direction: column; gap: 12px; margin-top: 10px; padding: 14px 16px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface-soft); transition: background .2s ease, border-color .2s ease; }\n" +
            ".chip-row, .metric-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }\n" +
            ".metric-row-progress { width: 100%; }\n" +
            ".metric-spacer { flex: 1 1 auto; }\n" +
            ".chip { display: inline-flex; align-items: center; min-height: 28px; padding: 0 12px; border-radius: 999px; border: 1px solid var(--border); background: var(--surface-muted); color: var(--text-soft); font-size: 12px; font-weight: 700; text-decoration: none; transition: background-color .2s ease, border-color .2s ease, color .2s ease; }\n" +
            ".chip-link { cursor: pointer; }\n" +
            ".chip-link:hover { background: var(--surface-muted-hover); }\n" +
            ".chip-recommend { border-width: 1.5px; }\n" +
            ".recommend-none { background: transparent; color: var(--muted); border-color: var(--border-strong); }\n" +
            ".recommend-good { background: rgba(39,174,96,0.12); color: #1f6f46; border-color: rgba(39,174,96,0.28); }\n" +
            ".recommend-warn { background: rgba(243,156,18,0.14); color: #995f00; border-color: rgba(243,156,18,0.34); }\n" +
            ".recommend-bad { background: rgba(231,76,60,0.12); color: #a03022; border-color: rgba(231,76,60,0.3); }\n" +
            ".metric-item { display: inline-flex; align-items: center; gap: 6px; min-height: 24px; }\n" +
            ".metric-label { color: var(--muted); font-size: 12px; font-weight: 600; }\n" +
            ".metric-value { color: var(--text); font-size: 12px; font-weight: 700; }\n" +
            ".metric-value-strong { font-size: 13px; }\n" +
            ".metric-separator { color: var(--muted); opacity: .8; font-size: 12px; font-weight: 700; }\n" +
            ".capsule-strip { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }\n" +
            ".section { background: var(--surface); border-radius: 12px; padding: 20px 24px; margin-bottom: 16px; box-shadow: var(--shadow); transition: background-color .2s ease, box-shadow .2s ease; }\n" +
            ".section h2 { font-size: 15px; font-weight: 600; margin-bottom: 14px; color: var(--text); }\n" +
            ".total { margin-bottom: 10px; color: var(--text-soft); }\n" +
            "table { border-collapse: collapse; width: 100%; }\n" +
            "th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid var(--table-border); }\n" +
            "th { font-weight: 600; color: var(--muted); font-size: 12px; }\n" +
            ".summary-layout { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }\n" +
            ".summary-donut-wrap { flex: 0 0 168px; display: flex; justify-content: center; }\n" +
            ".summary-donut { width: 168px; height: 168px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }\n" +
            ".summary-donut-hole { width: 102px; height: 102px; border-radius: 50%; background: var(--hole); display: flex; flex-direction: column; align-items: center; justify-content: center; box-shadow: inset 0 0 0 1px var(--border); transition: background-color .2s ease, box-shadow .2s ease; }\n" +
            ".summary-donut-total { font-size: 26px; font-weight: 800; color: var(--text); line-height: 1; }\n" +
            ".summary-donut-label { margin-top: 4px; font-size: 11px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; color: var(--muted); }\n" +
            ".summary-legend { flex: 1 1 320px; display: grid; gap: 10px; }\n" +
            ".summary-row { display: flex; align-items: center; gap: 12px; }\n" +
            ".summary-badge { display: inline-flex !important; align-items: center; justify-content: center; text-align: center; min-width: 180px; min-height: 30px; padding: 0 12px; border-radius: 999px; font-size: 11px; font-weight: 700; text-transform: none; letter-spacing: 0; line-height: 1.2; }\n" +
            ".summary-metrics { display: inline-flex; align-items: baseline; gap: 8px; font-size: 14px; color: var(--text); }\n" +
            ".summary-pct { font-size: 12px; font-weight: 700; color: var(--muted); }\n" +
            ".history-filter-group { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }\n" +
            ".history-filter-chip { appearance: none; border: 1px solid var(--border); background: transparent; color: var(--muted); border-radius: 999px; padding: 6px 12px; font-size: 12px; font-weight: 700; cursor: pointer; transition: background-color .15s ease, border-color .15s ease, color .15s ease; }\n" +
            ".history-filter-chip:hover { background: var(--surface-muted); color: var(--text); }\n" +
            ".history-filter-chip-active { background: var(--surface-muted-hover); border-color: var(--border-strong); color: var(--text); }\n" +
            ".history-empty-filtered { font-size: 12px; color: var(--muted); margin-bottom: 12px; }\n" +
            ".history-table.history-has-ordinal td:first-child, .history-table.history-has-ordinal th:first-child { width: 52px; text-align: center; }\n" +
            ".history-link-col { width: 46px; min-width: 46px; text-align: center; }\n" +
            ".history-task-link { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 6px; color: var(--text-soft); text-decoration: none; font-size: 12px; line-height: 1; transition: background-color .15s ease, color .15s ease; }\n" +
            ".history-task-link:hover { background: var(--surface-muted-hover); color: var(--text); }\n" +
            ".history-table td:last-child, .history-table th:last-child { white-space: nowrap; width: 148px; }\n" +
            ".trend { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 6px; }\n" +
            ".capsule { display: inline-flex; align-items: center; justify-content: center; height: 30px; border-radius: 999px; font-size: 12px; font-weight: 800; color: #fff; }\n" +
            ".capsule-wide { padding: 0 16px; min-width: 60px; }\n" +
            ".capsule-round { width: 30px; }\n" +
            ".cap-passed { background: #27ae60; }\n" +
            ".cap-bugs { background: #f39c12; }\n" +
            ".cap-failed { background: #e74c3c; }\n" +
            ".cap-unknown { background: #999; }\n" +
            ".muted { font-size: 11px; color: var(--muted); }\n" +
            ".badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; }\n" +
            ".status-passed { background: #d4f7dc; color: #1a7a35; }\n" +
            ".status-bugs { background: #fff3cd; color: #856404; }\n" +
            ".status-failed { background: #fde8e8; color: #c0392b; }\n" +
            ".status-critical { background: #f5c6cb; color: #721c24; }\n" +
            ".status-skipped { background: #e2e3e5; color: #495057; }\n" +
            ".status-progress { background: #cce5ff; color: #004085; }\n" +
            ".status-unknown { background: #f0f0f0; color: #888; }\n" +
            "footer { text-align: center; font-size: 11px; color: var(--muted); margin-top: 24px; padding-top: 12px; }\n" +
            "@media (max-width: 840px) { .top-meta-recommend { margin-left: 0; } .theme-toggle { margin-left: 0; } .summary-badge { min-width: 148px; } .summary-layout { gap: 16px; } }\n";

    private static final String JS =
            "(function(){\n" +
            "  var storageKey = 'testory-report-theme';\n" +
            "  var body = document.body;\n" +
            "  var toggle = document.querySelector('[data-theme-toggle]');\n" +
            "  function setTheme(theme){\n" +
            "    var next = theme === 'dark' ? 'dark' : 'light';\n" +
            "    body.setAttribute('data-theme', next);\n" +
            "    if (toggle) toggle.setAttribute('aria-pressed', String(next === 'dark'));\n" +
            "    try { localStorage.setItem(storageKey, next); } catch (e) {}\n" +
            "  }\n" +
            "  var savedTheme = null;\n" +
            "  try { savedTheme = localStorage.getItem(storageKey); } catch (e) {}\n" +
            "  if (!savedTheme && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {\n" +
            "    savedTheme = 'dark';\n" +
            "  }\n" +
            "  setTheme(savedTheme || 'light');\n" +
            "  if (toggle) {\n" +
            "    toggle.addEventListener('click', function(){\n" +
            "      setTheme(body.getAttribute('data-theme') === 'dark' ? 'light' : 'dark');\n" +
            "    });\n" +
            "  }\n" +
            "})();\n" +
            "document.querySelectorAll('[data-history-section]').forEach(function(section){\n" +
            "  var buttons = section.querySelectorAll('[data-history-filter]');\n" +
            "  var rows = section.querySelectorAll('[data-history-row]');\n" +
            "  var empty = section.querySelector('.history-empty-filtered');\n" +
            "  function apply(filter){\n" +
            "    var visible = 0;\n" +
            "    rows.forEach(function(row){\n" +
            "      var status = row.getAttribute('data-status') || '';\n" +
            "      var match = filter === '__ALL__' || (filter === '__NOT_STARTED__' ? status === '' : status === filter);\n" +
            "      row.hidden = !match;\n" +
            "      if (match) visible++;\n" +
            "    });\n" +
            "    if (empty) empty.hidden = visible !== 0;\n" +
            "    buttons.forEach(function(btn){\n" +
            "      btn.classList.toggle('history-filter-chip-active', btn.getAttribute('data-history-filter') === filter);\n" +
            "    });\n" +
            "  }\n" +
            "  buttons.forEach(function(btn){\n" +
            "    btn.addEventListener('click', function(){ apply(btn.getAttribute('data-history-filter') || '__ALL__'); });\n" +
            "  });\n" +
            "  apply('__ALL__');\n" +
            "});\n";

    private record HtmlMetric(String label, String value, boolean strongValue, String tooltipText) {
        boolean isEmpty() {
            return (label == null || label.isBlank()) && (value == null || value.isBlank());
        }
    }
}
