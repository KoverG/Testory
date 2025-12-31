package app.domain.testcases;

import app.domain.testcases.TestCase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TagStore {

    private static final Path FILE = Path.of("config", "tags.json");
    private static final String NL = System.lineSeparator();
    private static final char Q = '"';

    private TagStore() {}

    public static List<String> loadAll() {
        String json = readJsonSafe();
        // ВАЖНО: порядок как в JSON, без сортировки
        return parseArray(json);
    }

    public static void addAll(List<String> tags) {
        if (tags == null || tags.isEmpty()) return;

        List<String> existing = loadAll();
        boolean changed = false;

        for (String s : tags) {
            String v = norm(s);
            if (v.isEmpty()) continue;
            if (!containsIgnoreCase(existing, v)) {
                existing.add(v);
                changed = true;
            }
        }

        if (changed) saveAll(existing);
    }

    public static void saveAll(List<String> items) {
        if (items == null) items = new ArrayList<>();
        String json = toJson(items);
        writeJsonSafe(json);
    }

    // === NEW: пересобрать tags из карточек и перезаписать tags.json уникальными значениями ===
    public static void saveAllFromTestCases(List<TestCase> cases) {
        List<String> out = new ArrayList<>();
        if (cases != null) {
            for (TestCase tc : cases) {
                if (tc == null) continue;
                List<String> src = tc.getTags();
                if (src == null) continue;
                for (String v : src) {
                    String x = (v == null) ? "" : v.trim();
                    if (x.isEmpty()) continue;
                    if (!containsIgnoreCase(out, x)) out.add(x);
                }
            }
        }
        saveAll(out);
    }

    private static boolean containsIgnoreCase(List<String> list, String v) {
        if (list == null || list.isEmpty()) return false;
        for (String x : list) {
            if (x == null) continue;
            if (x.equalsIgnoreCase(v)) return true;
        }
        return false;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private static String readJsonSafe() {
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, "[]" + NL, StandardCharsets.UTF_8);
                return "[]";
            }

            byte[] bytes = Files.readAllBytes(FILE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static void writeJsonSafe(String json) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json + NL, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static String toJson(List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            String v = clean(items.get(i));
            sb.append(Q).append(v).append(Q);
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append(']');
        return sb.toString();
    }

    private static List<String> parseArray(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;

        int i = skipWs(json, 0);
        if (i >= json.length() || json.charAt(i) != '[') return out;
        i++;

        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length()) break;
            char c = json.charAt(i);
            if (c == ']') break;

            if (c == Q) {
                int j = json.indexOf(Q, i + 1);
                if (j < 0) break;
                String v = json.substring(i + 1, j);
                v = norm(v);
                if (!v.isEmpty()) out.add(v);
                i = j + 1;
            } else {
                i++;
            }

            i = skipWs(json, i);
            if (i < json.length() && json.charAt(i) == ',') i++;
        }

        return out;
    }

    private static String clean(String s) {
        if (s == null) return "";
        String x = s;
        x = x.replace(Q, '\'');
        x = x.replace('\r', ' ');
        x = x.replace('\n', ' ');
        x = x.trim();
        return x;
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
