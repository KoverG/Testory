package app.domain.cycles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Private per-user config for Cycles screen.
 *
 * Location:
 *   config/cycles/private-config.json
 *
 * Stores only "remember QA responsible" state for the Profile modal.
 */
public final class CyclePrivateConfig {

    private static final Path FILE = Path.of("config", "cycles", "private-config.json");

    private static final String KEY_REMEMBER_QA = "rememberQa";
    private static final String KEY_QA_NAME = "qaName";

    // ✅ env modal
    private static final String KEY_REMEMBER_ENV = "rememberEnv";
    private static final String KEY_ENV_MOBILE = "envMobile";

    // ✅ Added cases ComboBox: text -> color
    // "caseComboColors": { "Option A": "#FFB4B4", "Option B": "#B4FFB4" }
    private static final String KEY_CASE_COMBO_COLORS = "caseComboColors";

    private CyclePrivateConfig() {}

    public static boolean rememberQaEnabled() {
        String json = readJsonSafe();
        return readBoolField(json, KEY_REMEMBER_QA);
    }

    public static String rememberedQaName() {
        String json = readJsonSafe();
        String v = readStringField(json, KEY_QA_NAME);
        return v == null ? "" : v;
    }

    public static void setRememberQa(boolean remember, String qaName) {
        String json = readJsonSafe();

        String upd = upsertBoolField(json, KEY_REMEMBER_QA, remember);
        upd = upsertStringField(upd, KEY_QA_NAME, (qaName == null) ? "" : qaName.trim());

        writeJsonSafe(upd);
    }

    // ===================== ENV (remember) =====================

    public static boolean rememberEnvEnabled() {
        String json = readJsonSafe();
        return readBoolField(json, KEY_REMEMBER_ENV);
    }

    public static boolean rememberedEnvMobile() {
        String json = readJsonSafe();
        return readBoolField(json, KEY_ENV_MOBILE);
    }

    /**
     * Remember only env type (mobile/desktop). Builds value must NOT be stored here.
     */
    public static void setRememberEnv(boolean remember, boolean mobile) {
        String json = readJsonSafe();

        String upd = upsertBoolField(json, KEY_REMEMBER_ENV, remember);
        upd = upsertBoolField(upd, KEY_ENV_MOBILE, mobile);

        writeJsonSafe(upd);
    }

    // ===================== Added cases ComboBox =====================

    /**
     * @return ordered map: item text -> css color (e.g. "#FF00AA").
     * If missing/invalid -> empty map.
     */
    public static Map<String, String> caseComboColors() {
        String json = readJsonSafe();
        return readStringMapField(json, KEY_CASE_COMBO_COLORS);
    }

    // ===== FS =====

    private static String readJsonSafe() {
        try {
            if (!Files.exists(FILE)) return "{}";
            return Files.readString(FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[CYCLES-PRIVATE] Failed to read " + FILE + ": " + e.getMessage());
            return "{}";
        }
    }

    private static void writeJsonSafe(String json) {
        try {
            Path parent = FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(FILE, (json == null ? "{}" : json) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[CYCLES-PRIVATE] Failed to write " + FILE + ": " + e.getMessage());
        }
    }

    // ===== Minimal JSON helpers (object root only) =====
    // NOTE: config is controlled by app -> we keep helpers minimal, like PrivateRootConfig.

    private static boolean readBoolField(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return false;

        String k = quote(key);
        int i = json.indexOf(k);
        if (i < 0) return false;

        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return false;

        int p = colon + 1;
        p = skipWs(json, p);
        if (p >= json.length()) return false;

        if (startsWithIgnoreWs(json, p, "true")) return true;
        if (startsWithIgnoreWs(json, p, "false")) return false;

        return false;
    }

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

    /**
     * Reads map field of type:
     *   "key": { "A": "#fff", "B": "#000" }
     *
     * Only supports simple string:string pairs, no escapes handling (same philosophy as other helpers).
     */
    private static Map<String, String> readStringMapField(String json, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank() || key == null || key.isBlank()) return out;

        String k = quote(key);
        int i = json.indexOf(k);
        if (i < 0) return out;

        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return out;

        int p = colon + 1;
        p = skipWs(json, p);
        if (p >= json.length() || json.charAt(p) != '{') return out;

        int end = findObjectEnd(json, p);
        if (end <= p) return out;

        int cur = p + 1;
        while (cur < end) {
            cur = skipWs(json, cur);
            if (cur >= end) break;

            char c = json.charAt(cur);
            if (c == ',') { cur++; continue; }
            if (c == '}') break;

            if (c != q()) { cur++; continue; }

            int kEnd = findStringEndQuote(json, cur + 1);
            if (kEnd < 0 || kEnd >= end) break;

            String mapKey = json.substring(cur + 1, kEnd).trim();

            cur = kEnd + 1;
            cur = skipWs(json, cur);

            if (cur >= end || json.charAt(cur) != ':') break;
            cur++;

            cur = skipWs(json, cur);
            if (cur >= end || json.charAt(cur) != q()) break;

            int vEnd = findStringEndQuote(json, cur + 1);
            if (vEnd < 0 || vEnd >= end) break;

            String mapVal = json.substring(cur + 1, vEnd).trim();

            if (!mapKey.isBlank() && !mapVal.isBlank()) {
                out.put(mapKey, mapVal);
            }

            cur = vEnd + 1;
        }

        return out;
    }

    private static int findObjectEnd(String s, int openBracePos) {
        if (s == null) return -1;
        if (openBracePos < 0 || openBracePos >= s.length()) return -1;
        if (s.charAt(openBracePos) != '{') return -1;

        boolean inStr = false;
        for (int i = openBracePos + 1, depth = 1; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == q()) {
                inStr = !inStr;
                continue;
            }

            if (inStr) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
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

        String fieldLine = k + ": " + v;
        return insertField(json, fieldLine);
    }

    private static String upsertBoolField(String json, String key, boolean value) {
        if (json == null || json.isBlank()) json = "{}";
        if (key == null || key.isBlank()) return json;

        String k = quote(key);
        String v = value ? "true" : "false";

        int i = json.indexOf(k);
        if (i >= 0) {
            int colon = json.indexOf(':', i + k.length());
            if (colon < 0) return json;

            int p = colon + 1;
            p = skipWs(json, p);

            int end = p;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == ',' || c == '}' || c == '\n' || c == '\r') break;
                end++;
            }

            return json.substring(0, p) + v + json.substring(end);
        }

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
        return s.indexOf(q(), from);
    }

    private static int skipWs(String s, int i) {
        int p = i;
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') { p++; continue; }
            break;
        }
        return p;
    }

    private static boolean startsWithIgnoreWs(String s, int from, String token) {
        if (s == null || token == null) return false;
        int p = from;
        for (int i = 0; i < token.length(); i++) {
            int idx = p + i;
            if (idx >= s.length()) return false;
            if (s.charAt(idx) != token.charAt(i)) return false;
        }
        return true;
    }

    private static char q() {
        return (char) 34;
    }

    private static String quote(String s) {
        String qq = Character.toString(q());
        return qq + (s == null ? "" : s) + qq;
    }
}
