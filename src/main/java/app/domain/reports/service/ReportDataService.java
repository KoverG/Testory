package app.domain.reports.service;

import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.reports.model.ReportData;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.model.ReportTargetType;
import app.domain.testcases.TestCase;
import app.domain.testcases.repo.TestCaseCardJsonReader;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReportDataService {

    private static final Path CASES_ROOT = Path.of("test_resources", "test_cases");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DISP_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final List<ReportSectionProvider> providers;

    public ReportDataService() {
        CaseHistoryIndexStore historyStore = new CaseHistoryIndexStore();
        providers = List.of(
                new StatusSummarySectionProvider(historyStore),
                new TrendSectionProvider(historyStore),
                new HistorySectionProvider(historyStore)
        );
    }

    public ReportData build(ReportTarget target) {
        String title      = resolveTitle(target);
        String subtitle   = resolveSubtitle(target);
        String startedAt  = resolveStartedAt(target);
        String finishedAt = resolveFinishedAt(target);
        String lastDate   = resolveLastDate(target);

        List<ReportSection> sections = new ArrayList<>();
        for (ReportSectionProvider provider : providers) {
            Optional<ReportSection> section = provider.provide(target);
            section.ifPresent(sections::add);
        }

        return new ReportData(target, title, subtitle, startedAt, finishedAt, lastDate, sections);
    }

    // ===== вспомогательные =====

    private String resolveTitle(ReportTarget target) {
        if (target.type() == ReportTargetType.TEST_CASE) {
            TestCase tc = readCase(target.id());
            return tc != null && tc.getTitle() != null ? tc.getTitle() : target.id();
        } else {
            CycleDraft draft = readCycle(target);
            return draft != null && !draft.title.isBlank() ? draft.title : target.id();
        }
    }

    private String resolveSubtitle(ReportTarget target) {
        if (target.type() == ReportTargetType.TEST_CASE) {
            TestCase tc = readCase(target.id());
            if (tc == null) return "";
            String tags   = tc.tagsText();
            String labels = tc.labelsText();
            if (!labels.isBlank() && !tags.isBlank()) return labels + "  |  " + tags;
            if (!labels.isBlank()) return labels;
            return tags;
        } else {
            CycleDraft draft = readCycle(target);
            if (draft == null) return "";
            return draft.envType.isBlank() ? "" : draft.envType;
        }
    }

    private String resolveStartedAt(ReportTarget target) {
        if (target.type() != ReportTargetType.CYCLE) return "";
        CycleDraft draft = readCycle(target);
        if (draft == null) return "";
        return formatIso(draft.createdAtIso);
    }

    private String resolveFinishedAt(ReportTarget target) {
        if (target.type() != ReportTargetType.CYCLE) return "";
        CycleDraft draft = readCycle(target);
        if (draft == null) return "";
        return formatIso(draft.savedAtIso);
    }

    private String resolveLastDate(ReportTarget target) {
        if (target.type() == ReportTargetType.TEST_CASE) {
            CaseHistoryIndexStore store = new CaseHistoryIndexStore();
            List<CaseHistoryIndexStore.CycleHistoryEntry> entries = store.read(target.id());
            if (entries.isEmpty()) return "";
            var last = entries.get(entries.size() - 1);
            return last.createdAtUi();
        }
        return "";
    }

    private static String formatIso(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            // ISO может быть "2026-03-15T00:00:35.660582500" — обрезаем наносекунды
            String trimmed = iso.length() > 19 ? iso.substring(0, 19) : iso;
            LocalDateTime dt = LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return dt.format(DISP_FMT);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    private TestCase readCase(String caseId) {
        if (caseId == null || caseId.isBlank()) return null;
        Path file = CASES_ROOT.resolve(caseId.trim() + ".json");
        return TestCaseCardJsonReader.read(file);
    }

    private CycleDraft readCycle(ReportTarget target) {
        if (target.file() == null) return null;
        return CycleCardJsonReader.readDraft(target.file());
    }
}
