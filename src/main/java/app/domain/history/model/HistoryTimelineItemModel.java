package app.domain.history.model;

import java.time.LocalDateTime;

public record HistoryTimelineItemModel(
        String cycleId,
        LocalDateTime startedAt,
        String title,
        String runState,
        int totalCases,
        int passedCount,
        int passedWithBugsCount,
        int failedCount,
        int criticalFailedCount,
        int skippedCount,
        int inProgressCount,
        String qaResponsible,
        String environment
) {
    public int problemCount() {
        return failedCount + criticalFailedCount + passedWithBugsCount;
    }

    public boolean hasResponsible() {
        return qaResponsible != null && !qaResponsible.isBlank();
    }

    public boolean hasEnvironment() {
        return environment != null && !environment.isBlank();
    }
}