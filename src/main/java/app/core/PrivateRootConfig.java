// FILE: src/main/java/app/core/PrivateRootConfig.java
package app.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PrivateRootConfig {

    // приватный конфиг ДОЛЖЕН лежать тут:
    // config/cycles/private-config.json
    private static final Path FILE = Path.of("config", "cycles", "private-config.json");


    private static final String KEY_RIGHT_FIELD_1 = "rightField1";

    // ✅ QA users list (shared between screens)
    private static final String KEY_QA_USERS = "qaUsers";

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

    // ===================== QA USERS =====================

    public static List<String> qaUsers() {
        String json = readJsonSafe();
        List<String> xs = readStringArrayField(json, KEY_QA_USERS);
        if (xs == null) return List.of();

        ArrayList<String> out = new ArrayList<>();
        for (String s : xs) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static void addQaUser(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return;

        ArrayList<String> cur = new ArrayList<>(qaUsers());
        for (String x : cur) {
            if (x == null) continue;
            if (x.trim().equalsIgnoreCase(n)) return;
        }

        cur.add(n);

        String json = readJsonSafe();
        String upd = upsertStringArrayField(json, KEY_QA_USERS, cur);
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

    private static List<String> readStringArrayField(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return null;

        String k = quote(key);
        int i = json.indexOf(k);
        if (i < 0) return null;

        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return null;

        int p = colon + 1;
        p = skipWs(json, p);

        if (p >= json.length() || json.charAt(p) != '[') return null;

        int end = json.indexOf(']', p + 1);
        if (end < 0) return null;

        String body = json.substring(p + 1, end);

        ArrayList<String> out = new ArrayList<>();

        int idx = 0;
        while (idx < body.length()) {
            idx = skipWs(body, idx);
            if (idx >= body.length()) break;

            char c = body.charAt(idx);
            if (c == ',') { idx++; continue; }

            if (c != q()) { idx++; continue; }

            int q1 = idx;
            int q2 = body.indexOf(q(), q1 + 1);
            if (q2 < 0) break;

            String v = body.substring(q1 + 1, q2);
            out.add(v);

            idx = q2 + 1;
        }

        return out;
    }

    private static String upsertStringField(String json, String key, String value) {
        if (json == null || json.isBlank()) json = "{}";
        if (key == null || key == null || key.isBlank()) return json;
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

    private static String upsertStringArrayField(String json, String key, List<String> values) {
        if (json == null || json.isBlank()) json = "{}";
        if (key == null || key.isBlank()) return json;
        if (values == null) values = List.of();

        String k = quote(key);
        String v = arr(values);

        int i = json.indexOf(k);
        if (i >= 0) {
            int colon = json.indexOf(':', i + k.length());
            if (colon < 0) return json;

            int p = colon + 1;
            p = skipWs(json, p);
            if (p >= json.length() || json.charAt(p) != '[') return json;

            int end = json.indexOf(']', p + 1);
            if (end < 0) return json;

            return json.substring(0, p) + v + json.substring(end + 1);
        }

        String fieldLine = k + ": " + v;
        return insertField(json, fieldLine);
    }

    private static String arr(List<String> xs) {
        if (xs == null || xs.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder(xs.size() * 16 + 8);
        sb.append("[");
        int n = 0;

        for (String x : xs) {
            if (x == null) continue;
            String t = x.trim();
            if (t.isEmpty()) continue;

            if (n > 0) sb.append(", ");
            sb.append(quote(t));
            n++;
        }

        sb.append("]");
        return sb.toString();
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

    private static int skipWs(String s, int i) {
        int p = i;
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') p++;
            else break;
        }
        return p;
    }

    private static char q() {
        return (char) 34;
    }

    private static String quote(String s) {
        String qq = Character.toString(q());
        return qq + (s == null ? "" : s) + qq;
    }
}
