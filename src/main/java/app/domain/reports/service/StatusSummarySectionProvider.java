package app.domain.reports.service;

import app.domain.cycles.CaseStatusDefinition;
import app.domain.cycles.CaseStatusRegistry;
import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.model.ReportTargetType;
import app.domain.reports.model.StatusSummarySection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Провайдер секции STATUS_SUMMARY.
 * Считает количество прохождений по каждому статусу.
 */
public final class StatusSummarySectionProvider implements ReportSectionProvider {


    private final CaseHistoryIndexStore historyStore;

    public StatusSummarySectionProvider(CaseHistoryIndexStore historyStore) {
        this.historyStore = historyStore;
    }

    @Override
    public Optional<ReportSection> provide(ReportTarget target) {
        if (target.type() == ReportTargetType.TEST_CASE) {
            return buildForCase(target);
        } else if (target.type() == ReportTargetType.CYCLE) {
            return buildForCycle(target);
        }
        return Optional.empty();
    }

    private Optional<ReportSection> buildForCase(ReportTarget target) {
        List<CaseHistoryIndexStore.CycleHistoryEntry> entries = historyStore.read(target.id());
        if (entries.isEmpty()) return Optional.empty();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var entry : entries) {
            String status = entry.status();
            String normalized = status == null ? "" : status.trim().toUpperCase();
            counts.merge(normalized, 1, Integer::sum);
        }

        int total = entries.size();
        return Optional.of(new StatusSummarySection(total, orderedCounts(counts)));
    }

    private Optional<ReportSection> buildForCycle(ReportTarget target) {
        if (target.file() == null) return Optional.empty();

        CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
        if (draft == null || draft.cases == null || draft.cases.isEmpty()) return Optional.empty();

        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            total++;
            String status = ref.safeStatus();
            String normalized = status == null ? "" : status.trim().toUpperCase();
            counts.merge(normalized, 1, Integer::sum);
        }

        if (total == 0) return Optional.empty();
        return Optional.of(new StatusSummarySection(total, orderedCounts(counts)));
    }

    private static Map<String, Integer> orderedCounts(Map<String, Integer> counts) {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (CaseStatusDefinition definition : CaseStatusRegistry.orderedWith(counts)) {
            ordered.put(definition.code(), counts.getOrDefault(definition.code(), 0));
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }
}
