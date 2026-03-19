package app.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppSettings {

    private static final Path FILE = Path.of("config", "app-setting.json");

    private AppSettings() {}

    // selected=true => LIGHT (как у тебя в ShellController)
    public static boolean themeLight() {
        String json = readJsonSafe();
        Boolean v = readBooleanField(json, "themeLight");
        return v != null ? v : false;
    }

    public static void setThemeLight(boolean light) {
        String json = readJsonSafe();
        String upd = upsertBooleanField(json, "themeLight", light);
        writeJsonSafe(upd);
    }

    // ===== Language (i18n) =====

    public static String lang() {
        String json = readJsonSafe();
        String v = readStringField(json, "lang");
        return (v == null || v.isBlank()) ? "ru" : v;
    }

    public static void setLang(String langCode) {
        if (langCode == null || langCode.isBlank()) langCode = "ru";
        String json = readJsonSafe();
        String upd = upsertStringField(json, "lang", langCode);
        writeJsonSafe(upd);
    }

    public static boolean caseHistoryCollapsed() {
        String json = readJsonSafe();
        Boolean v = readBooleanField(json, "caseHistoryCollapsed");
        return v != null ? v : false;
    }

    public static void setCaseHistoryCollapsed(boolean collapsed) {
        String json = readJsonSafe();
        String upd = upsertBooleanField(json, "caseHistoryCollapsed", collapsed);
        writeJsonSafe(upd);
    }

    public static String historyScale() {
        String json = readJsonSafe();
        return normalizeHistoryScale(readStringField(json, "historyScale"));
    }

    public static void setHistoryScale(String scaleCode) {
        String normalized = normalizeHistoryScale(scaleCode);
        String json = readJsonSafe();
        String upd = upsertStringField(json, "historyScale", normalized);
        writeJsonSafe(upd);
    }

    private static String normalizeHistoryScale(String scaleCode) {
        if (scaleCode == null || scaleCode.isBlank()) {
            return "month";
        }

        String normalized = scaleCode.trim().toLowerCase();
        return switch (normalized) {
            case "week", "month", "year" -> normalized;
            default -> "month";
        };
    }

    // ===== File I/O =====

    private static String readJsonSafe() {
        try {
            if (!Files.exists(FILE)) return "{}";
            return Files.readString(FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[SETTINGS] Failed to read app-setting.json: " + e.getMessage());
            return "{}";
        }
    }

    private static void writeJsonSafe(String json) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[SETTINGS] Failed to write app-setting.json: " + e.getMessage());
        }
    }

    // ===== Minimal JSON helpers (object root only) =====

    private static String readStringField(String json, String key) {
        int k = indexOfKey(json, key);
        if (k < 0) return null;

        int colon = json.indexOf(':', k);
        if (colon < 0) return null;

        int i = skipWs(json, colon + 1);
        if (i >= json.length()) return null;

        if (json.charAt(i) != '"') return null;
        int j = json.indexOf('"', i + 1);
        if (j < 0) return null;

        return json.substring(i + 1, j);
    }

    private static Boolean readBooleanField(String json, String key) {
        int k = indexOfKey(json, key);
        if (k < 0) return null;

        int colon = json.indexOf(':', k);
        if (colon < 0) return null;

        int i = skipWs(json, colon + 1);
        if (i >= json.length()) return null;

        if (json.startsWith("true", i)) return true;
        if (json.startsWith("false", i)) return false;
        return null;
    }

    private static String upsertStringField(String json, String key, String value) {
        int k = indexOfKey(json, key);
        if (k >= 0) {
            int colon = json.indexOf(':', k);
            if (colon < 0) return json;

            int i = skipWs(json, colon + 1);
            if (i >= json.length()) return json;

            int end;
            if (json.charAt(i) == '"') {
                int j = json.indexOf('"', i + 1);
                if (j < 0) return json;
                end = j + 1;
            } else {
                end = i;
                while (end < json.length() && ",}\n\r\t ".indexOf(json.charAt(end)) < 0) end++;
            }

            String before = json.substring(0, i);
            String after = json.substring(end);

            String replacement = "\"" + value + "\"";
            return before + replacement + after;
        }

        return insertField(json, """
          "%s": "%s"
        """.formatted(key, value).trim());
    }

    private static String upsertBooleanField(String json, String key, boolean value) {
        int k = indexOfKey(json, key);
        if (k >= 0) {
            int colon = json.indexOf(':', k);
            if (colon < 0) return json;

            int i = skipWs(json, colon + 1);
            if (i >= json.length()) return json;

            int end = i;
            while (end < json.length() && ",}\n\r\t ".indexOf(json.charAt(end)) < 0) end++;

            String before = json.substring(0, i);
            String after = json.substring(end);

            return before + (value ? "true" : "false") + after;
        }

        return insertField(json, """
          "%s": %s
        """.formatted(key, value ? "true" : "false").trim());
    }

    private static String insertField(String json, String fieldLine) {
        int brace = json.indexOf('{');
        int close = json.lastIndexOf('}');
        if (brace < 0 || close < 0 || close <= brace) return "{\n  " + fieldLine + "\n}";

        String inside = json.substring(brace + 1, close).trim();
        if (inside.isEmpty()) {
            return "{\n  " + fieldLine + "\n}";
        }

        String sep = inside.endsWith(",") ? "" : ",";
        return "{\n" + inside + sep + "\n  " + fieldLine + "\n}";
    }

    private static int indexOfKey(String json, String key) {
        String needle = "\"" + key + "\"";
        return json.indexOf(needle);
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return i;
            i++;
        }
        return i;
    }
}