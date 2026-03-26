package app.domain.cycles;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CaseStatusRegistry {

    public static final String PASSED = "PASSED";
    public static final String PASSED_WITH_BUGS = "PASSED_WITH_BUGS";
    public static final String FAILED = "FAILED";
    public static final String CRITICAL_FAILED = "CRITICAL_FAILED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SKIPPED = "SKIPPED";

    private static final List<CaseStatusDefinition> DEFAULTS = List.of(
            new CaseStatusDefinition(PASSED, PASSED, "#27AE60", 1.0, PASSED),
            new CaseStatusDefinition(PASSED_WITH_BUGS, PASSED_WITH_BUGS, "#F39C12", 2.0, PASSED_WITH_BUGS),
            new CaseStatusDefinition(IN_PROGRESS, IN_PROGRESS, "#2980B9", 3.0, IN_PROGRESS),
            new CaseStatusDefinition(FAILED, FAILED, "#E74C3C", 4.0, FAILED),
            new CaseStatusDefinition(CRITICAL_FAILED, CRITICAL_FAILED, "#C0392B", 5.0, CRITICAL_FAILED),
            new CaseStatusDefinition(SKIPPED, SKIPPED, "#7F8C8D", 0.0, SKIPPED)
    );

    private CaseStatusRegistry() {
    }

    public static List<CaseStatusDefinition> ordered() {
        return mergeConfigured(Map.of());
    }

    public static List<CaseStatusDefinition> orderedWith(Map<String, Integer> countsByStatus) {
        return mergeConfigured(countsByStatus);
    }

    public static List<String> orderedCodes() {
        List<String> codes = new ArrayList<>();
        for (CaseStatusDefinition definition : ordered()) {
            codes.add(definition.code());
        }
        return codes;
    }

    public static String displayLabel(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) return "";

        for (CaseStatusDefinition definition : ordered()) {
            if (definition.code().equals(normalized)) {
                return definition.label();
            }
        }
        return code == null ? "" : code.trim();
    }

    public static String baseStatus(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) return "";

        for (CaseStatusDefinition definition : ordered()) {
            if (definition.code().equals(normalized)) {
                return definition.baseStatus();
            }
        }
        return normalized;
    }

    public static String color(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) return "#7F8C8D";

        for (CaseStatusDefinition definition : ordered()) {
            if (definition.code().equals(normalized)) {
                return definition.color();
            }
        }
        return "#7F8C8D";
    }

    public static Map<String, String> comboColors() {
        Map<String, String> out = new LinkedHashMap<>();
        for (CaseStatusDefinition definition : ordered()) {
            out.put(definition.code(), definition.color());
        }
        return out;
    }

    public static boolean isCompleted(String code) {
        String base = baseStatus(code);
        return !base.isBlank() && !IN_PROGRESS.equals(base);
    }

    public static boolean isInProgress(String code) {
        return IN_PROGRESS.equals(baseStatus(code));
    }

    public static boolean isWarning(String code) {
        return PASSED_WITH_BUGS.equals(baseStatus(code));
    }

    public static boolean isFailedLike(String code) {
        String base = baseStatus(code);
        return FAILED.equals(base) || CRITICAL_FAILED.equals(base);
    }

    public static String trendColorKey(String code) {
        return switch (baseStatus(code)) {
            case PASSED -> "passed";
            case PASSED_WITH_BUGS -> "bugs";
            case FAILED, CRITICAL_FAILED -> "failed";
            case SKIPPED -> "skipped";
            case IN_PROGRESS -> "progress";
            default -> "unknown";
        };
    }

    public static Color fxColor(String code) {
        return Color.web(color(code));
    }

    public static String reportBadgeStyle(String code) {
        String background = color(code);
        String textColor = isDarkColor(background) ? "#FFFFFF" : "#161616";
        return "-fx-background-color: " + background + "; -fx-text-fill: " + textColor + ";";
    }

    public static String htmlBadgeStyle(String code) {
        String background = color(code);
        String textColor = isDarkColor(background) ? "#FFFFFF" : "#161616";
        return "background: " + background + "; color: " + textColor + ";";
    }

    private static List<CaseStatusDefinition> mergeConfigured(Map<String, Integer> countsByStatus) {
        Map<String, CaseStatusDefinition> merged = new LinkedHashMap<>();
        for (CaseStatusDefinition definition : DEFAULTS) {
            merged.put(definition.code(), definition);
        }

        for (CaseStatusDefinition definition : CyclePrivateConfig.caseStatuses()) {
            merged.put(definition.code(), definition);
        }

        if (countsByStatus != null) {
            for (String rawCode : countsByStatus.keySet()) {
                String code = normalize(rawCode);
                if (code.isBlank() || merged.containsKey(code)) continue;
                merged.put(code, new CaseStatusDefinition(code, rawCode, "#7F8C8D", Double.MAX_VALUE - 1, code));
            }
        }

        List<CaseStatusDefinition> ordered = new ArrayList<>(merged.values());
        ordered.sort(Comparator.comparingDouble(CaseStatusDefinition::priority).thenComparing(CaseStatusDefinition::code));
        return ordered;
    }

    private static boolean isDarkColor(String color) {
        try {
            Color fx = Color.web(color);
            double brightness = (fx.getRed() * 299.0 + fx.getGreen() * 587.0 + fx.getBlue() * 114.0) / 1000.0;
            return brightness < 0.62;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
