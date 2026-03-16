package app.domain.history.model;

import java.time.LocalDate;

public record HistoryCalendarDayModel(
        LocalDate date,
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

    public boolean hasActive() {
        return activeCount > 0;
    }
}