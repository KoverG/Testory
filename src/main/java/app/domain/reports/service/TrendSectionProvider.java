package app.domain.reports.service;

import app.domain.cycles.CaseStatusRegistry;
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
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        if (target.type() == ReportTargetType.TEST_CASE) {
            List<CaseHistoryIndexStore.CycleHistoryEntry> entries = historyStore.read(target.id());
            for (var e : entries) {
                String colorKey = CaseStatusRegistry.trendColorKey(e.status());
                if (!"unknown".equals(colorKey) && !"progress".equals(colorKey) && !"skipped".equals(colorKey)) {
                    counts.merge(colorKey, 1, Integer::sum);
                }
            }
        } else if (target.type() == ReportTargetType.CYCLE) {
            if (target.file() == null) return Optional.empty();
            CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
            if (draft == null || draft.cases == null) return Optional.empty();
            for (CycleCaseRef ref : draft.cases) {
                if (ref == null) continue;
                String colorKey = CaseStatusRegistry.trendColorKey(ref.safeStatus());
                if (!"unknown".equals(colorKey) && !"progress".equals(colorKey) && !"skipped".equals(colorKey)) {
                    counts.merge(colorKey, 1, Integer::sum);
                }
            }
        }

        List<TrendCapsule> capsules = buildCapsules(counts);
        if (capsules.isEmpty()) return Optional.empty();
        return Optional.of(new TrendSection(capsules));
    }

    private static List<TrendCapsule> buildCapsules(java.util.Map<String, Integer> counts) {
        record Group(String key, int count) {}
        List<Group> groups = new ArrayList<>();
        for (String key : List.of("passed", "bugs", "failed")) {
            int count = counts.getOrDefault(key, 0);
            if (count > 0) groups.add(new Group(key, count));
        }

        if (groups.isEmpty()) return List.of();

        int max = groups.stream().mapToInt(Group::count).max().orElse(0);
        long topCount = groups.stream().filter(g -> g.count() == max).count();
        boolean uniqueTop = topCount == 1;

        List<TrendCapsule> capsules = new ArrayList<>();
        for (Group g : groups) {
            boolean wide = uniqueTop && g.count() == max;
            capsules.add(new TrendCapsule(g.key(), g.count(), wide));
        }
        return capsules;
    }
}
