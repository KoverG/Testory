package app.domain.history.service;

import app.domain.history.model.HistoryCalendarDayModel;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HistoryWeekDataService {

    private final HistoryMonthDataService monthDataService = new HistoryMonthDataService();

    public Map<LocalDate, HistoryCalendarDayModel> readWeek(LocalDate weekAnchor) {
        LocalDate weekStart = (weekAnchor == null ? LocalDate.now() : weekAnchor)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        Map<LocalDate, HistoryCalendarDayModel> pool = new LinkedHashMap<>(monthDataService.readMonth(weekStart));
        if (!YearMonth.from(weekStart).equals(YearMonth.from(weekEnd))) {
            pool.putAll(monthDataService.readMonth(weekEnd));
        }

        Map<LocalDate, HistoryCalendarDayModel> result = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            result.put(d, pool.getOrDefault(d, new HistoryCalendarDayModel(d, 0, 0, 0)));
        }
        return result;
    }
}
