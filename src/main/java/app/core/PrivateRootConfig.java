// FILE: src/main/java/app/core/PrivateRootConfig.java
package app.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PrivateRootConfig {

    // приватный конфиг ДОЛЖЕН лежать тут:
    // config/testcases/private-config.json
    private static final Path FILE = Path.of("config", "testcases", "private-config.json");

    private static final String KEY_RIGHT_FIELD_1 = "rightField1";

    private PrivateRootConfig() {}

    public static String rightField1() {
        String json = readJsonSafe();
        String v = readStringField(json, KEY_RIGHT_FIELD_1);
        return v == null ? "" : v;
    }

    public static void setRightField1(String value) {
        if (value == null) value = "";
        String json = readJsonSafe();
        String upd = upsertStringField(json, KEY_RIGHT_FIELD_1, value);
        writeJsonSafe(upd);
    }

    private static String readJsonSafe() {
        try {
            if (!Files.exists(FILE)) return "{}";
            return Files.readString(FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[PRIVATE] Failed to read " + FILE + ": " + e.getMessage());
            return "{}";
        }
    }

    private static void writeJsonSafe(String json) {
        try {
            Path parent = FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(FILE, json + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[PRIVATE] Failed to write " + FILE + ": " + e.getMessage());
        }
    }

    // ===== Minimal JSON helpers (object root only) =====

    private static String readStringField(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return null;

        String k = quote(key);
        int i = json.indexOf(k);
        if (i < 0) return null;

        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return null;

        int q1 = json.indexOf(q(), colon + 1);
        if (q1 < 0) return null;

        int q2 = findStringEndQuote(json, q1 + 1);
        if (q2 < 0) return null;

        return json.substring(q1 + 1, q2);
    }

    private static String upsertStringField(String json, String key, String value) {
        if (json == null || json.isBlank()) json = "{}";
        if (key == null || key.isBlank()) return json;
        if (value == null) value = "";

        String k = quote(key);
        String v = quote(value);

        int i = json.indexOf(k);
        if (i >= 0) {
            int colon = json.indexOf(':', i + k.length());
            if (colon < 0) return json;

            int q1 = json.indexOf(q(), colon + 1);
            if (q1 < 0) return json;

            int q2 = findStringEndQuote(json, q1 + 1);
            if (q2 < 0) return json;

            return json.substring(0, q1) + v + json.substring(q2 + 1);
        }

        // ключа нет -> аккуратно вставляем поле в объект с нормальными скобками/переносами
        String fieldLine = k + ": " + v;
        return insertField(json, fieldLine);
    }

    private static String insertField(String json, String fieldLine) {
        int brace = json.indexOf('{');
        int close = json.lastIndexOf('}');
        if (brace < 0 || close < 0 || close <= brace) {
            return "{\n  " + fieldLine + "\n}";
        }

        String inside = json.substring(brace + 1, close).trim();
        if (inside.isEmpty()) {
            return "{\n  " + fieldLine + "\n}";
        }

        String sep = inside.endsWith(",") ? "" : ",";
        return "{\n" + inside + sep + "\n  " + fieldLine + "\n}";
    }

    private static int findStringEndQuote(String s, int from) {
        // в твоём текущем JSON нет экранирования, поэтому просто ищем следующую кавычку
        return s.indexOf(q(), from);
    }

    private static char q() {
        return (char) 34;
    }

    private static String quote(String s) {
        String qq = Character.toString(q());
        return qq + (s == null ? "" : s) + qq;
    }
}
