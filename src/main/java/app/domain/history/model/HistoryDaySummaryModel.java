package app.domain.history.model;

import java.time.LocalDate;

public record HistoryDaySummaryModel(
        LocalDate date,
        int cycleCount,
        int problematicCount,
        int activeCount
) {
    public boolean hasActivity() {
        return cycleCount > 0;
    }
}