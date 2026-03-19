package app.domain.history.model;

import java.time.LocalDate;

public record HistoryCalendarDayModel(
        LocalDate date,
        int cycleCount,
        int failedCycleCount,
        int warningCycleCount,
        int activeCount,
        int finishedCount
) {
    public int problematicCount() {
        return failedCycleCount + warningCycleCount;
    }

    public int unfinishedCount() {
        return Math.max(0, cycleCount - finishedCount);
    }

    public boolean hasActivity() {
        return cycleCount > 0;
    }

    public boolean hasProblems() {
        return problematicCount() > 0;
    }

    public boolean hasActive() {
        return activeCount > 0;
    }

    public boolean hasFailures() {
        return failedCycleCount > 0;
    }

    public boolean hasWarnings() {
        return warningCycleCount > 0;
    }

    public boolean isAllPassed() {
        return cycleCount > 0
                && failedCycleCount == 0
                && warningCycleCount == 0
                && unfinishedCount() == 0;
    }
}