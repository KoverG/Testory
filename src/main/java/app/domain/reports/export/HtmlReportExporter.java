package app.domain.reports.export;

import app.domain.reports.model.HistorySection;
import app.domain.reports.model.ReportData;
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

/**
 * Генерирует самодостаточный HTML-файл отчёта и открывает его в браузере.
 * Добавление новой секции = добавление нового SectionRenderer в buildRenderers().
 */
public final class HtmlReportExporter {

    private static final DateTimeFormatter TS_FMT   = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Map<String, SectionRenderer> renderers;

    public HtmlReportExporter() {
        renderers = buildRenderers();
    }

    private static Map<String, SectionRenderer> buildRenderers() {
        Map<String, SectionRenderer> m = new LinkedHashMap<>();
        register(m, new StatusSummaryRenderer());
        register(m, new TrendRenderer());
        register(m, new HistoryRenderer());
        // новые рендереры добавлять сюда
        return m;
    }

    private static void register(Map<String, SectionRenderer> m, SectionRenderer r) {
        m.put(r.sectionType(), r);
    }

    /**
     * Генерирует HTML и открывает в браузере.
     * @param onError — колбэк с сообщением об ошибке (для UI)
     */
    public void export(ReportData data, Consumer<String> onError) {
        try {
            String html = buildHtml(data);
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

    private String buildHtml(ReportData data) {
        StringBuilder sb = new StringBuilder(8192);

        String genDate = LocalDateTime.now().format(DATE_FMT);
        String typeLabel = switch (data.target().type()) {
            case TEST_CASE -> "Тест-кейс";
            case CYCLE     -> "Цикл";
        };

        sb.append("<!DOCTYPE html>\n<html lang=\"ru\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>Отчёт: ").append(esc(data.title())).append("</title>\n");
        sb.append("<style>\n").append(CSS).append("</style>\n");
        sb.append("</head>\n<body>\n");

        // шапка
        sb.append("<header>\n");
        sb.append("  <div class=\"header-meta\">Testory — ").append(esc(typeLabel)).append("</div>\n");
        sb.append("  <h1>").append(esc(data.title())).append("</h1>\n");
        if (!data.subtitle().isBlank()) {
            sb.append("  <p class=\"subtitle\">").append(esc(data.subtitle())).append("</p>\n");
        }
        if (!data.startedAt().isBlank()) {
            sb.append("  <p class=\"meta-line\">Начат: <strong>")
              .append(esc(data.startedAt())).append("</strong></p>\n");
        }
        if (!data.finishedAt().isBlank()) {
            sb.append("  <p class=\"meta-line\">Завершён: <strong>")
              .append(esc(data.finishedAt())).append("</strong></p>\n");
        }
        if (!data.lastRunDate().isBlank()) {
            sb.append("  <p class=\"meta-line\">Последнее прохождение: <strong>")
              .append(esc(data.lastRunDate())).append("</strong></p>\n");
        }
        sb.append("  <p class=\"meta-line gen-date\">Сгенерировано: ").append(esc(genDate)).append("</p>\n");
        sb.append("</header>\n");

        // секции
        for (ReportSection section : data.sections()) {
            SectionRenderer renderer = renderers.get(section.sectionType());
            if (renderer != null) renderer.render(section, sb);
        }

        sb.append("<footer>Testory — система управления тестированием</footer>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void openInBrowser(Path file) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(file.toUri());
                return;
            }
        }
        // fallback для Windows
        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", file.toAbsolutePath().toString()});
    }

    private static String sanitize(String id) {
        if (id == null) return "unknown";
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    static String esc(String s) {
        return StatusSummaryRenderer.htmlEscape(s);
    }

    // ===== CSS =====

    private static final String CSS =
        "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
        "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n" +
        "  font-size: 14px; color: #1a1a2e; background: #f5f5f7; padding: 24px; }\n" +
        "header { background: #fff; border-radius: 12px; padding: 24px 28px;\n" +
        "  margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }\n" +
        ".header-meta { font-size: 11px; color: #888; text-transform: uppercase;\n" +
        "  letter-spacing: .06em; margin-bottom: 6px; }\n" +
        "h1 { font-size: 22px; font-weight: 700; margin-bottom: 6px; }\n" +
        ".subtitle { color: #555; margin-bottom: 4px; }\n" +
        ".meta-line { font-size: 12px; color: #888; margin-top: 4px; }\n" +
        ".section { background: #fff; border-radius: 12px; padding: 20px 24px;\n" +
        "  margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }\n" +
        ".section h2 { font-size: 15px; font-weight: 600; margin-bottom: 14px; color: #333; }\n" +
        ".total { margin-bottom: 10px; color: #555; }\n" +
        "table { border-collapse: collapse; width: 100%; }\n" +
        "th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #f0f0f0; }\n" +
        "th { font-weight: 600; color: #666; font-size: 12px; }\n" +
        ".trend { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 6px; }\n" +
        ".capsule { display: flex; align-items: center; justify-content: center;\n" +
        "  height: 30px; border-radius: 999px; font-size: 12px; font-weight: 800; }\n" +
        ".capsule-wide  { padding: 0 16px; min-width: 60px; }\n" +
        ".capsule-round { width: 30px; }\n" +
        ".cap-passed { background: #27ae60; color: #fff; }\n" +
        ".cap-bugs   { background: #f39c12; color: #fff; }\n" +
        ".cap-failed { background: #e74c3c; color: #fff; }\n" +
        ".cap-unknown{ background: #999;    color: #fff; }\n" +
        ".muted { font-size: 11px; color: #aaa; }\n" +
        ".badge { display: inline-block; padding: 2px 8px; border-radius: 4px;\n" +
        "  font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; }\n" +
        ".status-passed   { background: #d4f7dc; color: #1a7a35; }\n" +
        ".status-bugs     { background: #fff3cd; color: #856404; }\n" +
        ".status-failed   { background: #fde8e8; color: #c0392b; }\n" +
        ".status-critical { background: #f5c6cb; color: #721c24; }\n" +
        ".status-skipped  { background: #e2e3e5; color: #495057; }\n" +
        ".status-progress { background: #cce5ff; color: #004085; }\n" +
        ".status-unknown  { background: #f0f0f0; color: #888; }\n" +
        "footer { text-align: center; font-size: 11px; color: #bbb;\n" +
        "  margin-top: 24px; padding-top: 12px; }\n";
}
