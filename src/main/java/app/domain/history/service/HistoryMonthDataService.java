package app.domain.history.service;

import app.domain.cycles.CaseStatusRegistry;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;
import app.domain.history.model.HistoryCalendarDayModel;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HistoryMonthDataService {

    private static final Path CYCLES_ROOT = Path.of("test_resources", "cycles");
    private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    public Map<LocalDate, HistoryCalendarDayModel> readMonth(LocalDate monthAnchor) {
        YearMonth yearMonth = YearMonth.from(monthAnchor == null ? LocalDate.now() : monthAnchor);
        Map<LocalDate, MutableDayStats> stats = new LinkedHashMap<>();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            stats.put(date, new MutableDayStats(date));
        }

        if (!Files.isDirectory(CYCLES_ROOT)) {
            return toImmutable(stats);
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(CYCLES_ROOT, "*.json")) {
            for (Path file : ds) {
                if (file == null) continue;

                CycleDraft draft = CycleCardJsonReader.readDraft(file);
                if (draft == null) continue;

                LocalDate createdDate = parseCreatedDate(draft);
                if (createdDate == null || !YearMonth.from(createdDate).equals(yearMonth)) continue;

                MutableDayStats day = stats.computeIfAbsent(createdDate, MutableDayStats::new);
                CycleFlags flags = evaluateCycle(draft);

                day.cycleCount++;
                if (flags.failedLike) {
                    day.failedCycleCount++;
                } else if (flags.warning) {
                    day.warningCycleCount++;
                }
                if (flags.active) {
                    day.activeCount++;
                }
                if (flags.finished) {
                    day.finishedCount++;
                }
            }
        } catch (Exception ignore) {
            // Keep the screen usable even if the history source is partially broken.
        }

        return toImmutable(stats);
    }

    private static Map<LocalDate, HistoryCalendarDayModel> toImmutable(Map<LocalDate, MutableDayStats> stats) {
        Map<LocalDate, HistoryCalendarDayModel> out = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, MutableDayStats> entry : stats.entrySet()) {
            MutableDayStats value = entry.getValue();
            out.put(entry.getKey(), new HistoryCalendarDayModel(
                    value.date,
                    value.cycleCount,
                    value.failedCycleCount,
                    value.warningCycleCount,
                    value.activeCount,
                    value.finishedCount
            ));
        }
        return out;
    }

    private static LocalDate parseCreatedDate(CycleDraft draft) {
        String iso = safe(draft.createdAtIso);
        if (!iso.isEmpty()) {
            try {
                return LocalDateTime.parse(iso).toLocalDate();
            } catch (DateTimeParseException ignore) {
                // fallback to UI date below
            }
        }

        String ui = safe(draft.createdAtUi);
        if (!ui.isEmpty()) {
            try {
                return LocalDate.parse(ui, UI_DATE_FORMATTER);
            } catch (DateTimeParseException ignore) {
                // no valid date
            }
        }

        return null;
    }

    private static CycleFlags evaluateCycle(CycleDraft draft) {
        boolean failedLike = false;
        boolean warning = false;

        if (draft != null && draft.cases != null) {
            for (CycleCaseRef ref : draft.cases) {
                if (ref == null) continue;
                String status = safe(ref.safeStatus()).toUpperCase(Locale.ROOT);
                switch (status) {
                    case "FAILED", "CRITICAL_FAILED" -> failedLike = true;
                    case "PASSED_WITH_BUGS" -> warning = true;
                    default -> {
                    }
                }
            }
        }

        boolean active = isActive(draft);
        boolean finished = draft != null && CycleRunState.isFinished(draft.runState);
        if (failedLike) {
            warning = false;
        }

        return new CycleFlags(failedLike, warning, active, finished);
    }

    private static boolean isActive(CycleDraft draft) {
        if (draft == null) return false;
        if (CycleRunState.isActive(draft.runState)) return true;
        if (draft.cases == null) return false;

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            if (CaseStatusRegistry.isInProgress(ref.safeStatus())) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record CycleFlags(boolean failedLike, boolean warning, boolean active, boolean finished) {
    }

    private static final class MutableDayStats {
        private final LocalDate date;
        private int cycleCount;
        private int failedCycleCount;
        private int warningCycleCount;
        private int activeCount;
        private int finishedCount;

        private MutableDayStats(LocalDate date) {
            this.date = date;
        }
    }
}