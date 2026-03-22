package app.domain.reports.service;

import app.domain.reports.model.ReportSection;
import app.domain.reports.model.ReportTarget;

import java.util.Optional;

/**
 * Контракт провайдера одной секции отчёта.
 * Каждая новая метрика реализует этот интерфейс — остальной код не меняется.
 */
public interface ReportSectionProvider {
    Optional<ReportSection> provide(ReportTarget target);
}
