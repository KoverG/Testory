package app.domain.reports.model;

/**
 * Маркерный интерфейс секции отчёта.
 * Каждая новая метрика — отдельный класс, реализующий этот интерфейс.
 */
public interface ReportSection {
    String sectionType();
}
