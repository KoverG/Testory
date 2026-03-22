package app.domain.reports.service;

import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.reports.model.HistorySection;
import app.domain.reports.model.HistorySection.HistoryRow;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.model.ReportTargetType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Провайдер секции HISTORY.
 * Для кейса — список всех прохождений по циклам.
 * Для цикла — список кейсов с их статусами (снимок текущего прохождения).
 */
public final class HistorySectionProvider implements ReportSectionProvider {

    private final CaseHistoryIndexStore historyStore;

    public HistorySectionProvider(CaseHistoryIndexStore historyStore) {
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

        List<HistoryRow> rows = new ArrayList<>();
        for (var e : entries) {
            rows.add(new HistoryRow(
                    e.cycleId(),
                    "",                   // название кейса не нужно — он в шапке
                    e.status(),
                    e.comment(),
                    e.createdAtUi(),
                    e.cycleTitle().isBlank() ? "Одиночный" : e.cycleTitle()
            ));
        }

        return Optional.of(new HistorySection(rows));
    }

    private Optional<ReportSection> buildForCycle(ReportTarget target) {
        if (target.file() == null) return Optional.empty();

        CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
        if (draft == null || draft.cases == null || draft.cases.isEmpty()) return Optional.empty();

        List<HistoryRow> rows = new ArrayList<>();
        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            String title = ref.safeTitleSnapshot().isBlank() ? ref.safeId() : ref.safeTitleSnapshot();
            rows.add(new HistoryRow(
                    ref.safeId(),
                    title,
                    ref.safeStatus(),
                    ref.safeComment(),
                    draft.createdAtUi,
                    ""
            ));
        }

        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(new HistorySection(rows));
    }
}
