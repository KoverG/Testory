package app.domain.reports.model;

/**
 * Factual meta-summary for the report header.
 */
public record ReportMetaSummary(
        String category,
        String environment,
        String qaResponsible,
        String taskLabel,
        String taskUrl,
        String startedAt,
        String lifecycleLabel,
        String lifecycleValue,
        String duration,
        String durationFull,
        int totalCases,
        int completedCases,
        int completionPercent
) {
    public boolean hasContext() {
        return !safe(category).isBlank()
                || !safe(environment).isBlank()
                || !safe(qaResponsible).isBlank()
                || !safe(taskLabel).isBlank();
    }

    public boolean hasTiming() {
        return !safe(startedAt).isBlank()
                || !safe(lifecycleLabel).isBlank()
                || !safe(duration).isBlank();
    }

    public boolean hasProgress() {
        return totalCases > 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
