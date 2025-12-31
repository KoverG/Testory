// FILE: src/main/java/app/domain/testcases/TestCaseStore.java
package app.domain.testcases;

import app.core.I18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TestCaseStore {

    private static final Path FILE = Path.of("config", "testcases.json");
    private static final String NL = System.lineSeparator();
    private static final char Q = '"';

    private TestCaseStore() {}

    public static List<TestCase> loadAll() {
        String json = readJsonSafe();
        List<TestCase> items = parseArray(json);

        items.sort(Comparator.comparing(TestCase::getId, Comparator.nullsLast(String::compareToIgnoreCase)));
        return items;
    }

    public static void saveAll(List<TestCase> items) {
        if (items == null) items = new ArrayList<>();
        String json = toJson(items);
        writeJsonSafe(json);
    }

    public static TestCase createDefault(List<TestCase> existing) {
        String id = nextId(existing);

        TestCase tc = new TestCase();
        tc.setId(id);

        // i18n
        tc.setTitle(I18n.t("tc.default.title"));
        tc.setDescription("");
        tc.setLabels(new ArrayList<>());
        tc.setTags(new ArrayList<>());

        List<String> steps = new ArrayList<>();
        steps.add(I18n.t("tc.default.stepPrefix") + " 1");
        steps.add(I18n.t("tc.default.stepPrefix") + " 2");
        tc.setSteps(steps);

        return tc;
    }

    // ===================== IO =====================

    private static String readJsonSafe() {
        try {
            if (!Files.exists(FILE)) return "[]";
            return Files.readString(FILE, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static void writeJsonSafe(String json) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json == null ? "[]" : json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    // ===================== PARSE/SERIALIZE (как в бандле) =====================

    private static List<TestCase> parseArray(String json) {
        List<TestCase> out = new ArrayList<>();
        if (json == null) return out;

        String s = json.trim();
        if (s.isEmpty()) return out;

        int i = s.indexOf('[');
        if (i < 0) return out;

        int j = findMatching(s, i, '[', ']');
        if (j < 0) return out;

        String arr = s.substring(i + 1, j);
        int p = 0;

        while (p < arr.length()) {
            p = skipWs(arr, p);
            if (p >= arr.length()) break;

            if (arr.charAt(p) == '{') {
                int q = findMatching(arr, p, '{', '}');
                if (q < 0) break;

                String obj = arr.substring(p, q + 1);
                TestCase tc = parseObject(obj);
                if (tc != null) out.add(tc);

                p = q + 1;
            } else {
                p++;
            }

            p = skipWs(arr, p);
            if (p < arr.length() && arr.charAt(p) == ',') p++;
        }

        return out;
    }

    private static TestCase parseObject(String obj) {
        TestCase tc = new TestCase();

        tc.setId(readString(obj, "id"));
        tc.setTitle(readString(obj, "title"));
        tc.setDescription(readString(obj, "description"));

        tc.setLabels(readStringArray(obj, "labels"));
        tc.setTags(readStringArray(obj, "tags"));
        tc.setSteps(readStringArray(obj, "steps"));

        if (tc.getId() == null || tc.getId().isBlank()) return null;
        return tc;
    }

    private static String toJson(List<TestCase> items) {
        if (items == null) items = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append('[').append(NL);

        for (int i = 0; i < items.size(); i++) {
            TestCase t = items.get(i);
            if (t == null) continue;

            sb.append("  {").append(NL);
            sb.append("    ").append(qKey("id")).append(": ").append(q(nz(t.getId()))).append(',').append(NL);
            sb.append("    ").append(qKey("title")).append(": ").append(q(nz(t.getTitle()))).append(',').append(NL);
            sb.append("    ").append(qKey("description")).append(": ").append(q(nz(t.getDescription()))).append(',').append(NL);

            sb.append("    ").append(qKey("labels")).append(": ").append(arr(t.getLabels())).append(',').append(NL);
            sb.append("    ").append(qKey("tags")).append(": ").append(arr(t.getTags())).append(',').append(NL);
            sb.append("    ").append(qKey("steps")).append(": ").append(arr(t.getSteps())).append(NL);

            sb.append("  }");
            if (i < items.size() - 1) sb.append(',');
            sb.append(NL);
        }

        sb.append(']').append(NL);
        return sb.toString();
    }

    private static String nextId(List<TestCase> existing) {
        int max = 0;
        if (existing != null) {
            for (TestCase t : existing) {
                if (t == null) continue;
                String id = t.getId();
                if (id == null) continue;
                String digits = id.replaceAll("[^0-9]", "");
                if (digits.isEmpty()) continue;
                try {
                    int v = Integer.parseInt(digits);
                    if (v > max) max = v;
                } catch (Exception ignored) {}
            }
        }
        return "TC-" + (max + 1);
    }

    private static String readString(String obj, String key) {
        String needle = qKey(key);
        int k = obj.indexOf(needle);
        if (k < 0) return "";

        int i = k + needle.length();
        i = skipWs(obj, i);
        if (i >= obj.length() || obj.charAt(i) != ':') return "";

        i++;
        i = skipWs(obj, i);
        if (i >= obj.length() || obj.charAt(i) != Q) return "";

        int j = obj.indexOf(Q, i + 1);
        if (j < 0) return "";

        return obj.substring(i + 1, j);
    }

    private static List<String> readStringArray(String obj, String key) {
        List<String> out = new ArrayList<>();
        String needle = qKey(key);
        int k = obj.indexOf(needle);
        if (k < 0) return out;

        int i = k + needle.length();
        i = skipWs(obj, i);
        if (i >= obj.length() || obj.charAt(i) != ':') return out;

        i++;
        i = skipWs(obj, i);
        if (i >= obj.length() || obj.charAt(i) != '[') return out;

        int j = findMatching(obj, i, '[', ']');
        if (j < 0) return out;

        String arr = obj.substring(i + 1, j);
        int p = 0;
        while (p < arr.length()) {
            p = skipWs(arr, p);
            if (p >= arr.length()) break;

            if (arr.charAt(p) == Q) {
                int q = arr.indexOf(Q, p + 1);
                if (q < 0) break;
                String v = arr.substring(p + 1, q);
                if (!v.isBlank()) out.add(v);
                p = q + 1;
            } else {
                p++;
            }

            p = skipWs(arr, p);
            if (p < arr.length() && arr.charAt(p) == ',') p++;
        }

        return out;
    }

    private static String qKey(String key) {
        return q(clean(key));
    }

    private static String arr(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            String v = clean(list.get(i));
            sb.append(q(v));
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String q(String v) {
        if (v == null) v = "";
        return String.valueOf(Q) + v + Q;
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

    private static int findMatching(String s, int from, char open, char close) {
        int depth = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
