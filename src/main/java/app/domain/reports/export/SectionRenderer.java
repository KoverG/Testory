package app.domain.reports.export;

import app.domain.reports.model.ReportSection;

/**
 * Контракт рендерера одной секции в HTML.
 * Каждая новая секция — новый рендерер, подключается в HtmlReportExporter.
 */
public interface SectionRenderer {
    String sectionType();
    void render(ReportSection section, StringBuilder out);
}
