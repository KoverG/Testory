package app.domain.testcases.repo;

import app.domain.testcases.TestCase;
import app.domain.testcases.usecase.TestCaseDraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestCaseCardJsonReader {

    private static final char Q = '\"';
    private static final char BS = (char) 92;
    private static final char NL = (char) 10;
    private static final char CR = (char) 13;
    private static final char TAB = (char) 9;

    private TestCaseCardJsonReader() {}

    // === для списка LEFT ===
    public static TestCase read(Path file) {
        if (file == null) return null;

        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        TestCase tc = new TestCase();

        String metaId = readMetaString(json, "id");
        if (metaId.isBlank()) metaId = baseName(file);
        tc.setId(metaId);

        tc.setCreatedAt(readMetaString(json, "createdAt"));
        tc.setSavedAt(readMetaString(json, "savedAt"));

        tc.setCode(readString(json, "code"));
        tc.setNumber(readString(json, "number"));

        tc.setTitle(readString(json, "title"));
        tc.setDescription(readString(json, "description"));
        tc.setLabels(readStringArray(json, "labels"));
        tc.setTags(readStringArray(json, "tags"));

        return tc;
    }

    // === для открытия RIGHT с заполнением ===
    public static TestCaseDraft readDraft(Path file) {
        if (file == null) return null;

        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        TestCaseDraft d = new TestCaseDraft();

        d.id = readMetaString(json, "id");
        d.createdAt = readMetaString(json, "createdAt");
        d.savedAt = readMetaString(json, "savedAt");

        d.code = readString(json, "code");
        d.number = readString(json, "number");
        d.title = readString(json, "title");
        d.description = readString(json, "description");

        d.labels = readStringArray(json, "labels");
        d.tags = readStringArray(json, "tags");

        d.steps = readSteps(json);

        return d;
    }

    private static String readMetaString(String json, String key) {
        if (json == null || key == null) return "";

        int m = json.indexOf(Q + "meta" + Q);
        if (m < 0) return "";

        m = json.indexOf('{', m);
        if (m < 0) return "";

        int mend = findObjectEnd(json, m);
        if (mend <= m) return "";

        String metaObj = json.substring(m, mend + 1);
        return readString(metaObj, key);
    }

    private static List<TestCaseDraft.StepDraft> readSteps(String json) {
        List<TestCaseDraft.StepDraft> out = new ArrayList<>();
        if (json == null) return out;

        int i = json.indexOf(Q + "steps" + Q);
        if (i < 0) return out;

        i = json.indexOf('[', i);
        if (i < 0) return out;

        i++;
        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length()) break;

            char c = json.charAt(i);
            if (c == ']') break;

            if (c == ',') {
                i++;
                continue;
            }

            if (c != '{') {
                i++;
                continue;
            }

            int objStart = i;
            int objEnd = findObjectEnd(json, objStart);
            if (objEnd <= objStart) break;

            String obj = json.substring(objStart, objEnd + 1);

            TestCaseDraft.StepDraft s = new TestCaseDraft.StepDraft();
            s.step = readString(obj, "step");
            s.data = readString(obj, "data");
            s.expected = readString(obj, "expected");

            out.add(s);

            i = objEnd + 1;
            i = skipWs(json, i);
            if (i < json.length() && json.charAt(i) == ',') i++;
        }

        return out;
    }

    private static int findObjectEnd(String json, int objStart) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;

        for (int i = objStart; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inStr) {
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == BS) {
                    esc = true;
                    continue;
                }
                if (c == Q) {
                    inStr = false;
                }
                continue;
            }

            if (c == Q) {
                inStr = true;
                continue;
            }

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String readString(String json, String key) {
        if (json == null || key == null) return "";
        int i = json.indexOf(Q + key + Q);
        if (i < 0) return "";
        i = json.indexOf(':', i);
        if (i < 0) return "";
        i++;
        i = skipWs(json, i);
        if (i >= json.length() || json.charAt(i) != Q) return "";
        int j = findStringEnd(json, i + 1);
        if (j < 0) return "";
        return unescape(json.substring(i + 1, j));
    }

    private static List<String> readStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        if (json == null || key == null) return out;

        int i = json.indexOf(Q + key + Q);
        if (i < 0) return out;

        i = json.indexOf(':', i);
        if (i < 0) return out;

        i++;
        i = skipWs(json, i);

        if (i >= json.length() || json.charAt(i) != '[') return out;

        i++;
        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length()) break;

            char c = json.charAt(i);
            if (c == ']') break;

            if (c == ',') {
                i++;
                continue;
            }

            if (c != Q) {
                i++;
                continue;
            }

            int j = findStringEnd(json, i + 1);
            if (j < 0) break;

            String s = unescape(json.substring(i + 1, j));
            if (!s.isBlank() && !out.contains(s)) out.add(s);

            i = j + 1;
        }

        return out;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == NL || c == CR || c == TAB) i++;
            else break;
        }
        return i;
    }

    private static int findStringEnd(String s, int from) {
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);

            if (esc) {
                esc = false;
                continue;
            }

            if (c == BS) {
                esc = true;
                continue;
            }

            if (c == Q) return i;
        }
        return -1;
    }

    private static String unescape(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != BS) {
                out.append(c);
                continue;
            }

            if (i + 1 >= s.length()) break;
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append(NL);
                case 'r' -> out.append(CR);
                case 't' -> out.append(TAB);
                case Q -> out.append(Q);
                case BS -> out.append(BS);
                default -> out.append(n);
            }
        }
        return out.toString();
    }
}
