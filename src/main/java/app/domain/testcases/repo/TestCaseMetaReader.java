package app.domain.testcases.repo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestCaseMetaReader {

    public static final class Meta {
        public String code = "";
        public String number = "";
        public String title = "";
    }

    private TestCaseMetaReader() {}

    public static Meta read(Path file) throws IOException {
        Meta m = new Meta();
        if (file == null) return m;

        String s = Files.readString(file, StandardCharsets.UTF_8);
        if (s == null || s.isBlank()) return m;

        m.code = readTopLevelString(s, "code");
        m.number = readTopLevelString(s, "number");
        m.title = readTopLevelString(s, "title");
        return m;
    }

    // Ищем "key": "value" на верхнем уровне (в твоём JSON это подходит идеально)
    private static String readTopLevelString(String json, String key) {
        if (json == null || key == null || key.isBlank()) return "";

        String needle = "\"" + key + "\"";
        int p = json.indexOf(needle);
        if (p < 0) return "";

        int colon = json.indexOf(':', p + needle.length());
        if (colon < 0) return "";

        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return "";

        StringBuilder out = new StringBuilder();
        boolean esc = false;

        for (int i = q1 + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (esc) {
                // поддержим минимум экранирований, которых у тебя может и не быть
                if (c == 'n') out.append((char) 10);
                else if (c == 'r') out.append((char) 13);
                else if (c == 't') out.append((char) 9);
                else out.append(c);
                esc = false;
                continue;
            }

            if (c == (char) 92) { // backslash
                esc = true;
                continue;
            }

            if (c == '"') break;

            out.append(c);
        }

        return out.toString().trim();
    }
}
