package app.domain.reports.service;

import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.model.ReportTargetType;
import app.domain.reports.model.TrendSection;
import app.domain.reports.model.TrendSection.TrendCapsule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Провайдер секции TREND.
 * Группирует прохождения в 3 цвета: PASSED, BUGS, FAILED+CRITICAL.
 * Правило размера капсулы:
 *   - wide  если count > min_count среди отображаемых капсул
 *   - round если count == min_count (или все одинаковые → все round)
 */
public final class TrendSectionProvider implements ReportSectionProvider {

    private final CaseHistoryIndexStore historyStore;

    public TrendSectionProvider(CaseHistoryIndexStore historyStore) {
        this.historyStore = historyStore;
    }

    @Override
    public Optional<ReportSection> provide(ReportTarget target) {
        int passed = 0, bugs = 0, failed = 0;

        if (target.type() == ReportTargetType.TEST_CASE) {
            List<CaseHistoryIndexStore.CycleHistoryEntry> entries = historyStore.read(target.id());
            for (var e : entries) {
                switch (e.status()) {
                    case "PASSED"           -> passed++;
                    case "PASSED_WITH_BUGS" -> bugs++;
                    case "FAILED", "CRITICAL_FAILED" -> failed++;
                }
            }
        } else if (target.type() == ReportTargetType.CYCLE) {
            if (target.file() == null) return Optional.empty();
            CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
            if (draft == null || draft.cases == null) return Optional.empty();
            for (CycleCaseRef ref : draft.cases) {
                if (ref == null) continue;
                switch (ref.safeStatus()) {
                    case "PASSED"           -> passed++;
                    case "PASSED_WITH_BUGS" -> bugs++;
                    case "FAILED", "CRITICAL_FAILED" -> failed++;
                }
            }
        }

        List<TrendCapsule> capsules = buildCapsules(passed, bugs, failed);
        if (capsules.isEmpty()) return Optional.empty();
        return Optional.of(new TrendSection(capsules));
    }

    private static List<TrendCapsule> buildCapsules(int passed, int bugs, int failed) {
        // собираем только ненулевые группы
        record Group(String key, int count) {}
        List<Group> groups = new ArrayList<>();
        if (passed > 0) groups.add(new Group("passed", passed));
        if (bugs   > 0) groups.add(new Group("bugs",   bugs));
        if (failed > 0) groups.add(new Group("failed", failed));

        if (groups.isEmpty()) return List.of();

        int min = groups.stream().mapToInt(Group::count).min().orElse(0);
        int max = groups.stream().mapToInt(Group::count).max().orElse(0);

        // Если все значения одинаковы (min==max) — все круглые
        boolean allEqual = (min == max);

        List<TrendCapsule> capsules = new ArrayList<>();
        for (Group g : groups) {
            boolean wide = !allEqual && g.count() > min;
            capsules.add(new TrendCapsule(g.key(), g.count(), wide));
        }
        return capsules;
    }
}
