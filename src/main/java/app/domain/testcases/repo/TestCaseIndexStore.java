package app.domain.testcases.repo;

import app.domain.testcases.TestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TestCaseIndexStore {

    private static final Path ROOT = Path.of("test_resources", "test_cases");
    private static final Path INDEX = ROOT.resolve("index.csv");

    private static final char NL = (char) 10;
    private static final char CR = (char) 13;

    private TestCaseIndexStore() {}

    public static void rebuild(List<TestCase> all) {
        ensureDir(ROOT);

        StringBuilder sb = new StringBuilder(4096);

        // Заголовок
        sb.append("id;file;code;number;title;savedAt").append(NL);

        if (all != null) {
            for (TestCase tc : all) {
                if (tc == null) continue;

                String id = s(tc.getId());
                if (id.isEmpty()) continue;

                String file = id + ".json";

                sb.append(csv(id)).append(';')
                        .append(csv(file)).append(';')
                        .append(csv(s(tc.getCode()))).append(';')
                        .append(csv(s(tc.getNumber()))).append(';')
                        .append(csv(s(tc.getTitle()))).append(';')
                        .append(csv(s(tc.getSavedAt())))
                        .append(NL);
            }
        }

        writeUtf8(INDEX, sb.toString());
    }

    private static String csv(String raw) {
        String v = raw == null ? "" : raw;
        if (v.isEmpty()) return "";

        boolean needQuotes = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == ';' || c == '"' || c == NL || c == CR) {
                needQuotes = true;
                break;
            }
        }

        if (!needQuotes) return v;

        StringBuilder out = new StringBuilder(v.length() + 8);
        out.append('"');

        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"') out.append('"').append('"');  // CSV escaping
            else if (c == NL || c == CR) out.append(' ');
            else out.append(c);
        }

        out.append('"');
        return out.toString();
    }

    private static String s(String v) {
        if (v == null) return "";
        return v.trim();
    }

    private static void ensureDir(Path dir) {
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException ignored) {}
    }

    private static void writeUtf8(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
