package app.domain.history.model;

import java.time.YearMonth;

public record HistoryMonthSummaryModel(
        YearMonth yearMonth,
        int cycleCount,
        int problematicCount,
        int activeCount
) {
    public boolean hasActivity() {
        return cycleCount > 0;
    }

    public boolean hasProblems() {
        return problematicCount > 0;
    }
}
