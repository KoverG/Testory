package app.domain.history.service;

import app.domain.history.model.HistoryCalendarDayModel;
import app.domain.history.model.HistoryMonthSummaryModel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HistoryYearDataService {

    private final HistoryMonthDataService monthDataService = new HistoryMonthDataService();

    public Map<YearMonth, HistoryMonthSummaryModel> readYear(LocalDate yearAnchor) {
        int year = (yearAnchor == null ? LocalDate.now() : yearAnchor).getYear();
        Map<YearMonth, HistoryMonthSummaryModel> result = new LinkedHashMap<>();

        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            Map<LocalDate, HistoryCalendarDayModel> days = monthDataService.readMonth(ym.atDay(1));
            int totalCycles      = days.values().stream().mapToInt(HistoryCalendarDayModel::cycleCount).sum();
            int totalProblematic = days.values().stream().mapToInt(HistoryCalendarDayModel::problematicCount).sum();
            int totalActive      = days.values().stream().mapToInt(HistoryCalendarDayModel::activeCount).sum();
            result.put(ym, new HistoryMonthSummaryModel(ym, totalCycles, totalProblematic, totalActive));
        }

        return result;
    }
}
