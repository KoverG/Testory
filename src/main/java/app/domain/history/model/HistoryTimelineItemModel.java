package app.domain.history.model;

import java.time.LocalDateTime;

public record HistoryTimelineItemModel(
        String cycleId,
        LocalDateTime occurredAt,
        String title,
        String eventType,
        String statusLabel,
        String runState,
        int totalCases,
        int passedCount,
        int passedWithBugsCount,
        int failedCount,
        int criticalFailedCount,
        int skippedCount,
        int inProgressCount,
        String qaResponsible,
        String environment,
        boolean problematicCycle,
        boolean notStartedCycle,
        boolean pausedCycle
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

    public boolean isStartedEvent() {
        return "started".equals(eventType);
    }

    public boolean isPausedEvent() {
        return "paused".equals(eventType);
    }

    public boolean isFinishedEvent() {
        return "finished".equals(eventType);
    }

    public boolean isNotStartedEvent() {
        return "not_started".equals(eventType);
    }
}