package app.domain.history.service;

import app.domain.cycles.CaseStatusRegistry;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HistoryDayDataService {

    private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    public HistoryDayDataModel readDay(LocalDate day) {
        LocalDate targetDay = day == null ? LocalDate.now() : day;
        List<HistoryTimelineItemModel> timeline = new ArrayList<>();
        Set<String> cycleIds = new HashSet<>();
        Set<String> problematicCycleIds = new HashSet<>();
        Set<String> notStartedCycleIds = new HashSet<>();
        Set<String> pausedCycleIds = new HashSet<>();

        Path cyclesRoot = new FileCycleRepository().rootDir();
        if (!Files.isDirectory(cyclesRoot)) {
            return new HistoryDayDataModel(
                    new HistoryDaySummaryModel(targetDay, 0, 0, 0, 0),
                    List.of()
            );
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cyclesRoot, "*.json")) {
            for (Path file : ds) {
                if (file == null) {
                    continue;
                }

                CycleDraft draft = CycleCardJsonReader.readDraft(file);
                if (draft == null) {
                    continue;
                }

                LocalDateTime createdAt = parseCreatedAt(draft);
                LocalDateTime startedAt = parseIso(safe(draft.runStartedAtIso));
                LocalDateTime savedAt = parseIso(safe(draft.savedAtIso));
                String runState = CycleRunState.normalize(draft.runState);
                CaseCounters counters = countCases(draft);

                boolean problematicCycle = counters.hasHardProblems();
                boolean notStartedCycle = CycleRunState.isIdle(runState) && startedAt == null;
                boolean pausedCycle = CycleRunState.isPaused(runState);

                if (notStartedCycle && sameDay(createdAt, targetDay)) {
                    timeline.add(buildTimelineItem(
                            draft,
                            createdAt,
                            "not_started",
                            "Не начат",
                            runState,
                            counters,
                            problematicCycle,
                            true,
                            false
                    ));
                    registerCycle(cycleIds, problematicCycleIds, notStartedCycleIds, pausedCycleIds,
                            safe(draft.id), problematicCycle, true, false);
                }

                if (sameDay(startedAt, targetDay)) {
                    timeline.add(buildTimelineItem(
                            draft,
                            startedAt,
                            "started",
                            CycleRunState.isPaused(runState) ? "На паузе" : "В процессе",
                            runState,
                            counters,
                            problematicCycle,
                            false,
                            pausedCycle
                    ));
                    registerCycle(cycleIds, problematicCycleIds, notStartedCycleIds, pausedCycleIds,
                            safe(draft.id), problematicCycle, false, pausedCycle);
                }

                if (pausedCycle && sameDay(savedAt, targetDay)) {
                    timeline.add(buildTimelineItem(
                            draft,
                            savedAt,
                            "paused",
                            "На паузе",
                            runState,
                            counters,
                            problematicCycle,
                            false,
                            true
                    ));
                    registerCycle(cycleIds, problematicCycleIds, notStartedCycleIds, pausedCycleIds,
                            safe(draft.id), problematicCycle, false, true);
                }

                if (CycleRunState.isFinished(runState) && sameDay(savedAt, targetDay)) {
                    timeline.add(buildTimelineItem(
                            draft,
                            savedAt,
                            "finished",
                            finishStatusLabel(counters),
                            runState,
                            counters,
                            problematicCycle,
                            false,
                            false
                    ));
                    registerCycle(cycleIds, problematicCycleIds, notStartedCycleIds, pausedCycleIds,
                            safe(draft.id), problematicCycle, false, false);
                }
            }
        } catch (Exception ignore) {
            // Keep history screen usable even if some cycle files are broken.
        }

        timeline.sort(Comparator.comparing(HistoryTimelineItemModel::occurredAt).reversed());
        HistoryDaySummaryModel summary = new HistoryDaySummaryModel(
                targetDay,
                cycleIds.size(),
                problematicCycleIds.size(),
                notStartedCycleIds.size(),
                pausedCycleIds.size()
        );
        return new HistoryDayDataModel(summary, timeline);
    }

    private static HistoryTimelineItemModel buildTimelineItem(
            CycleDraft draft,
            LocalDateTime occurredAt,
            String eventType,
            String statusLabel,
            String runState,
            CaseCounters counters,
            boolean problematicCycle,
            boolean notStartedCycle,
            boolean pausedCycle
    ) {
        LocalDateTime safeOccurredAt = occurredAt == null ? LocalDateTime.MIN : occurredAt;
        return new HistoryTimelineItemModel(
                safe(draft.id),
                safeOccurredAt,
                safe(draft.title),
                eventType,
                statusLabel,
                runState,
                counters.totalCases,
                counters.passedCount,
                counters.passedWithBugsCount,
                counters.failedCount,
                counters.criticalFailedCount,
                counters.skippedCount,
                counters.inProgressCount,
                safe(draft.qaResponsible),
                environmentLabel(draft),
                problematicCycle,
                notStartedCycle,
                pausedCycle
        );
    }

    private static void registerCycle(
            Set<String> cycleIds,
            Set<String> problematicCycleIds,
            Set<String> notStartedCycleIds,
            Set<String> pausedCycleIds,
            String cycleId,
            boolean problematicCycle,
            boolean notStartedCycle,
            boolean pausedCycle
    ) {
        if (cycleId.isBlank()) {
            return;
        }
        cycleIds.add(cycleId);
        if (problematicCycle) {
            problematicCycleIds.add(cycleId);
        }
        if (notStartedCycle) {
            notStartedCycleIds.add(cycleId);
        }
        if (pausedCycle) {
            pausedCycleIds.add(cycleId);
        }
    }

    private static LocalDateTime parseCreatedAt(CycleDraft draft) {
        LocalDateTime createdAt = parseIso(safe(draft.createdAtIso));
        if (createdAt != null) {
            return createdAt;
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

    private static LocalDateTime parseIso(String iso) {
        if (iso.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(iso);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private static boolean sameDay(LocalDateTime dateTime, LocalDate targetDay) {
        return dateTime != null && targetDay != null && targetDay.equals(dateTime.toLocalDate());
    }

    private static String finishStatusLabel(CaseCounters counters) {
        if (counters.hasHardProblems()) {
            return "Есть проблемы";
        }
        if (counters.passedWithBugsCount > 0) {
            return CaseStatusRegistry.displayLabel(CaseStatusRegistry.PASSED_WITH_BUGS);
        }
        if (counters.totalCases > 0) {
            return "Успешно";
        }
        return "Завершён";
    }

    private static CaseCounters countCases(CycleDraft draft) {
        CaseCounters counters = new CaseCounters();
        if (draft == null || draft.cases == null) {
            return counters;
        }

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) {
                continue;
            }

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

        private boolean hasHardProblems() {
            return failedCount > 0 || criticalFailedCount > 0;
        }
    }
}