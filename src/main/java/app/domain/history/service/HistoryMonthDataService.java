package app.domain.history.service;

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
                day.cycleCount++;
                if (isProblematic(draft)) day.problematicCount++;
                if (isActive(draft)) day.activeCount++;
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
                    value.problematicCount,
                    value.activeCount
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

    private static boolean isProblematic(CycleDraft draft) {
        if (draft == null || draft.cases == null) return false;

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            String status = safe(ref.safeStatus()).toUpperCase(Locale.ROOT);
            if ("FAILED".equals(status)
                    || "CRITICAL_FAILED".equals(status)
                    || "PASSED_WITH_BUGS".equals(status)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActive(CycleDraft draft) {
        if (draft == null) return false;
        if (CycleRunState.isActive(draft.runState)) return true;
        if (draft.cases == null) return false;

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;
            if ("IN_PROGRESS".equalsIgnoreCase(safe(ref.safeStatus()))) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableDayStats {
        private final LocalDate date;
        private int cycleCount;
        private int problematicCount;
        private int activeCount;

        private MutableDayStats(LocalDate date) {
            this.date = date;
        }
    }
}