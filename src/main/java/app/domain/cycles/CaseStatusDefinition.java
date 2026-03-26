package app.domain.cycles;

import java.util.Locale;

public record CaseStatusDefinition(
        String code,
        String label,
        String color,
        double priority,
        String baseStatus
) {
    public CaseStatusDefinition {
        code = normalizeCode(code);
        label = safe(label);
        color = normalizeColor(color);
        baseStatus = normalizeCode(baseStatus);

        if (label.isBlank()) {
            label = code;
        }
        if (baseStatus.isBlank()) {
            baseStatus = code;
        }
    }

    private static String normalizeCode(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizeColor(String value) {
        String normalized = safe(value);
        if (normalized.isEmpty()) {
            return "#7F8C8D";
        }
        return normalized.startsWith("#") ? normalized : "#" + normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
