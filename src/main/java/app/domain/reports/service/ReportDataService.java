package app.domain.reports.service;

import app.core.I18n;
import app.domain.cycles.CaseStatusRegistry;
import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;
import app.domain.reports.model.ReportData;
import app.domain.reports.model.ReportMetaSummary;
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
    private static final DateTimeFormatter DISP_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final List<ReportSectionProvider> providers;
    private final CaseHistoryIndexStore historyStore = new CaseHistoryIndexStore();

    public ReportDataService() {
        providers = List.of(
                new StatusSummarySectionProvider(historyStore),
                new TrendSectionProvider(historyStore),
                new HistorySectionProvider(historyStore)
        );
    }

    public ReportData build(ReportTarget target) {
        String title = resolveTitle(target);
        String subtitle = resolveSubtitle(target);
        String caseLabelsText = resolveCaseLabelsText(target);
        String caseTagsText = resolveCaseTagsText(target);
        String startedAt = resolveStartedAt(target);
        String finishedAt = resolveFinishedAt(target);
        String lastDate = resolveLastDate(target);
        ReportMetaSummary metaSummary = resolveMetaSummary(target);

        List<ReportSection> sections = new ArrayList<>();
        for (ReportSectionProvider provider : providers) {
            Optional<ReportSection> section = provider.provide(target);
            section.ifPresent(sections::add);
        }

        return new ReportData(target, title, subtitle, caseLabelsText, caseTagsText, startedAt, finishedAt, lastDate, metaSummary, sections);
    }

    private String resolveTitle(ReportTarget target) {
        if (target.type() == ReportTargetType.TEST_CASE) {
            TestCase tc = readCase(target.id());
            return buildCaseDisplayTitle(tc, target.id());
        }

        CycleDraft draft = readCycle(target);
        return draft != null && !draft.title.isBlank() ? draft.title : target.id();
    }

    private String resolveSubtitle(ReportTarget target) {
        return "";
    }

    private String resolveCaseLabelsText(ReportTarget target) {
        if (target.type() != ReportTargetType.TEST_CASE) {
            return "";
        }
        TestCase tc = readCase(target.id());
        return tc == null ? "" : safe(tc.labelsText());
    }

    private String resolveCaseTagsText(ReportTarget target) {
        if (target.type() != ReportTargetType.TEST_CASE) {
            return "";
        }
        TestCase tc = readCase(target.id());
        return tc == null ? "" : safe(tc.tagsText());
    }

    private String resolveStartedAt(ReportTarget target) {
        if (target.type() != ReportTargetType.CYCLE) return "";
        CycleDraft draft = readCycle(target);
        return draft == null ? "" : formatIso(draft.createdAtIso);
    }

    private String resolveFinishedAt(ReportTarget target) {
        if (target.type() != ReportTargetType.CYCLE) return "";
        CycleDraft draft = readCycle(target);
        if (draft == null || !CycleRunState.isFinished(draft.runState)) return "";
        return formatIso(draft.savedAtIso);
    }

    private String resolveLastDate(ReportTarget target) {
        if (target.type() != ReportTargetType.TEST_CASE) {
            return "";
        }

        List<CaseHistoryIndexStore.CycleHistoryEntry> entries = historyStore.read(target.id());
        if (entries.isEmpty()) return "";
        return entries.get(entries.size() - 1).createdAtUi();
    }

    private ReportMetaSummary resolveMetaSummary(ReportTarget target) {
        if (target.type() != ReportTargetType.CYCLE) {
            return null;
        }

        CycleDraft draft = readCycle(target);
        if (draft == null) {
            return null;
        }

        int totalCases = 0;
        int completedCases = 0;
        for (CycleCaseRef ref : safeCases(draft)) {
            totalCases++;
            if (CaseStatusRegistry.isCompleted(ref.safeStatus())) {
                completedCases++;
            }
        }

        int completionPercent = totalCases > 0 ? (int) Math.round(completedCases * 100.0 / totalCases) : 0;
        String runState = CycleRunState.normalize(draft.runState);
        String lifecycleLabel = lifecycleLabel(runState);
        String lifecycleValue = CycleRunState.isFinished(runState) ? formatIso(draft.savedAtIso) : "";

        return new ReportMetaSummary(
                safe(draft.category),
                environmentLabel(draft.envType),
                safe(draft.qaResponsible),
                resolveTaskLabel(draft),
                safe(draft.taskLinkUrl),
                formatIso(draft.createdAtIso),
                lifecycleLabel,
                lifecycleValue,
                formatDuration(draft.runElapsedSeconds),
                formatDurationFull(draft.runElapsedSeconds),
                totalCases,
                completedCases,
                completionPercent
        );
    }

    private static List<CycleCaseRef> safeCases(CycleDraft draft) {
        return draft.cases == null ? List.of() : draft.cases;
    }

    private static String resolveTaskLabel(CycleDraft draft) {
        String title = safe(draft.taskLinkTitle);
        if (!title.isBlank()) {
            return title;
        }

        String url = safe(draft.taskLinkUrl);
        if (url.isBlank()) {
            return "";
        }

        String compact = url
                .replaceFirst("^https?://", "")
                .replaceFirst("/$", "");
        return compact.length() > 28 ? compact.substring(0, 28) + "..." : compact;
    }

    private static String lifecycleLabel(String runState) {
        return switch (CycleRunState.normalize(runState)) {
            case CycleRunState.RUNNING -> tr("rp.meta.lifecycle.running", "В работе");
            case CycleRunState.PAUSED -> tr("rp.meta.lifecycle.paused", "На паузе");
            case CycleRunState.FINISHED -> tr("rp.meta.lifecycle.finished", "Завершен");
            default -> tr("rp.meta.lifecycle.updated", "Обновлен");
        };
    }

    private static String environmentLabel(String raw) {
        return switch (safe(raw).toLowerCase()) {
            case "mobile" -> tr("rp.meta.environment.mobile", "Mobile");
            case "desktop" -> tr("rp.meta.environment.desktop", "Desktop");
            default -> safe(raw);
        };
    }

    private static String formatDuration(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long totalMinutes = safeSeconds / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return String.format("%02d:%02d", hours, minutes);
    }

    private static String formatDurationFull(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatIso(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String tr(String key, String fallback) {
        String value = I18n.t(key);
        if (value == null || value.isBlank() || value.equals("!" + key + "!")) {
            return fallback;
        }
        return value;
    }

    private static String buildCaseDisplayTitle(TestCase tc, String fallbackId) {
        if (tc == null) {
            return safe(fallbackId);
        }

        String code = safe(tc.getCode());
        String number = safe(tc.getNumber());
        String title = safe(tc.getTitle());

        String head;
        if (!code.isEmpty() && !number.isEmpty()) {
            head = code + "-" + number;
        } else if (!code.isEmpty()) {
            head = code;
        } else {
            head = safe(tc.getId());
            if (head.isEmpty()) {
                head = safe(fallbackId);
            }
        }

        if (title.isEmpty()) {
            return head;
        }
        if (head.isEmpty()) {
            return title;
        }
        return head + " " + title;
    }
}
