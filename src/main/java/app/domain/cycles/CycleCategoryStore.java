package app.domain.cycles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CycleCategoryStore {

    private static final Path FILE = Path.of("config", "cycle-categories.json");
    private static final String NL = System.lineSeparator();
    private static final char Q = '"';

    private CycleCategoryStore() {}

    public static List<String> loadAll() {
        return parseArray(readJsonSafe());
    }

    public static void add(String category) {
        String value = norm(category);
        if (value.isEmpty()) return;

        List<String> existing = loadAll();
        if (containsIgnoreCase(existing, value)) return;
        existing.add(value);
        saveAll(existing);
    }

    public static void addAll(List<String> categories) {
        if (categories == null || categories.isEmpty()) return;

        List<String> existing = loadAll();
        boolean changed = false;

        for (String category : categories) {
            String value = norm(category);
            if (value.isEmpty()) continue;
            if (containsIgnoreCase(existing, value)) continue;
            existing.add(value);
            changed = true;
        }

        if (changed) saveAll(existing);
    }

    public static void saveAll(List<String> items) {
        if (items == null) items = new ArrayList<>();
        writeJsonSafe(toJson(items));
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
        } catch (Exception ignore) {
        }
    }

    private static String toJson(List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String item : items) {
            String value = clean(item);
            if (value.isEmpty()) continue;
            if (first) sb.append(NL);
            else sb.append(',').append(NL);
            sb.append(Q).append(value).append(Q);
            first = false;
        }
        if (!first) sb.append(NL);
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
                String value = norm(json.substring(i + 1, j));
                if (!value.isEmpty() && !containsIgnoreCase(out, value)) out.add(value);
                i = j + 1;
            } else {
                i++;
            }

            i = skipWs(json, i);
            if (i < json.length() && json.charAt(i) == ',') i++;
        }

        return out;
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) return false;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clean(String value) {
        String result = norm(value);
        result = result.replace(Q, '\'');
        result = result.replace('\r', ' ');
        result = result.replace('\n', ' ');
        return result.trim();
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
