package app.domain.cycles.usecase;

public final class CycleRunState {

    public static final String IDLE = "idle";
    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String FINISHED = "finished";

    private CycleRunState() {
    }

    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        return switch (value) {
            case RUNNING -> RUNNING;
            case PAUSED -> PAUSED;
            case FINISHED -> FINISHED;
            default -> IDLE;
        };
    }

    public static boolean isRunning(String raw) {
        return RUNNING.equals(normalize(raw));
    }

    public static boolean isPaused(String raw) {
        return PAUSED.equals(normalize(raw));
    }

    public static boolean isFinished(String raw) {
        return FINISHED.equals(normalize(raw));
    }

    public static boolean isIdle(String raw) {
        return IDLE.equals(normalize(raw));
    }

    public static boolean isActive(String raw) {
        String value = normalize(raw);
        return RUNNING.equals(value) || PAUSED.equals(value);
    }
}