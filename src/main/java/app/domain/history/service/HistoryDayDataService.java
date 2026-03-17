package app.domain.history.service;

import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.repo.FileCycleRepository;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;
import app.domain.history.model.HistoryDayDataModel;
import app.domain.history.model.HistoryDaySummaryModel;
import app.domain.history.model.HistoryTimelineItemModel;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class HistoryDayDataService {

    private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    public HistoryDayDataModel readDay(LocalDate day) {
        LocalDate targetDay = day == null ? LocalDate.now() : day;
        List<HistoryTimelineItemModel> timeline = new ArrayList<>();
        int problematicCount = 0;
        int activeCount = 0;

        Path cyclesRoot = new FileCycleRepository().rootDir();
        if (!Files.isDirectory(cyclesRoot)) {
            return new HistoryDayDataModel(
                    new HistoryDaySummaryModel(targetDay, 0, 0, 0),
                    List.of()
            );
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cyclesRoot, "*.json")) {
            for (Path file : ds) {
                if (file == null) continue;

                CycleDraft draft = CycleCardJsonReader.readDraft(file);
                if (draft == null) continue;

                LocalDateTime startedAt = parseStartedAt(draft);
                if (startedAt == null || !targetDay.equals(startedAt.toLocalDate())) continue;

                CaseCounters counters = countCases(draft);
                if (counters.problematic()) {
                    problematicCount++;
                }
                if (counters.active() || CycleRunState.isActive(draft.runState)) {
                    activeCount++;
                }

                timeline.add(new HistoryTimelineItemModel(
                        safe(draft.id),
                        startedAt,
                        safe(draft.title),
                        CycleRunState.normalize(draft.runState),
                        counters.totalCases,
                        counters.passedCount,
                        counters.passedWithBugsCount,
                        counters.failedCount,
                        counters.criticalFailedCount,
                        counters.skippedCount,
                        counters.inProgressCount,
                        safe(draft.qaResponsible),
                        environmentLabel(draft)
                ));
            }
        } catch (Exception ignore) {
            // Keep history screen usable even if some cycle files are broken.
        }

        timeline.sort(Comparator.comparing(HistoryTimelineItemModel::startedAt));
        HistoryDaySummaryModel summary = new HistoryDaySummaryModel(
                targetDay,
                timeline.size(),
                problematicCount,
                activeCount
        );
        return new HistoryDayDataModel(summary, timeline);
    }

    private static LocalDateTime parseStartedAt(CycleDraft draft) {
        String iso = safe(draft.createdAtIso);
        if (!iso.isEmpty()) {
            try {
                return LocalDateTime.parse(iso);
            } catch (DateTimeParseException ignore) {
                // fall back to createdAtUi below
            }
        }

        String ui = safe(draft.createdAtUi);
        if (!ui.isEmpty()) {
            try {
                return LocalDate.parse(ui, UI_DATE_FORMATTER).atStartOfDay();
            } catch (DateTimeParseException ignore) {
                // no valid date
            }
        }

        return null;
    }

    private static CaseCounters countCases(CycleDraft draft) {
        CaseCounters counters = new CaseCounters();
        if (draft == null || draft.cases == null) {
            return counters;
        }

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;

            counters.totalCases++;
            String status = safe(ref.safeStatus()).toUpperCase(Locale.ROOT);
            switch (status) {
                case "PASSED" -> counters.passedCount++;
                case "PASSED_WITH_BUGS" -> counters.passedWithBugsCount++;
                case "FAILED" -> counters.failedCount++;
                case "CRITICAL_FAILED" -> counters.criticalFailedCount++;
                case "SKIPPED" -> counters.skippedCount++;
                case "IN_PROGRESS" -> counters.inProgressCount++;
                default -> {
                }
            }
        }

        return counters;
    }

    private static String environmentLabel(CycleDraft draft) {
        if (draft == null) return "";

        String envType = safe(draft.envType).toLowerCase(Locale.ROOT);
        String envUrl = safe(draft.envUrl);
        if (!envUrl.isEmpty()) {
            return switch (envType) {
                case "mobile" -> "Mobile - Builds";
                case "desktop" -> "Desktop - Builds";
                default -> "Builds";
            };
        }

        return switch (envType) {
            case "mobile" -> "Mobile";
            case "desktop" -> "Desktop";
            default -> "";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CaseCounters {
        private int totalCases;
        private int passedCount;
        private int passedWithBugsCount;
        private int failedCount;
        private int criticalFailedCount;
        private int skippedCount;
        private int inProgressCount;

        private boolean problematic() {
            return passedWithBugsCount > 0 || failedCount > 0 || criticalFailedCount > 0;
        }

        private boolean active() {
            return inProgressCount > 0;
        }
    }
}