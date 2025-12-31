package app.domain.testcases.repo;

import app.domain.testcases.usecase.TestCaseDraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileTestCaseRepository implements TestCaseRepository {

    private static final Path ROOT = Path.of("test_resources", "test_cases");

    @Override
    public Path rootDir() {
        return ROOT;
    }

    @Override
    public Path saveNew(TestCaseDraft draft) {
        if (draft == null) draft = new TestCaseDraft();

        ensureDir(ROOT);

        ensureStableId(draft);

        Path file = fileById(draft.id);

        // extremely rare collision: regenerate
        int guard = 0;
        while (Files.exists(file) && guard < 1000) {
            draft.id = TestCaseDraft.newStableId();
            file = fileById(draft.id);
            guard++;
        }

        String json = TestCaseJson.toJson(draft);
        writeUtf8(file, json);

        return file;
    }

    @Override
    public Path saveExisting(Path file, TestCaseDraft draft) {
        if (file == null) throw new IllegalArgumentException("File is null");
        if (draft == null) draft = new TestCaseDraft();

        Path dir = file.getParent();
        if (dir == null) dir = ROOT;

        ensureDir(dir);

        ensureStableId(draft);

        // migration: existing file may be legacy (name-based); we always want <id>.json
        Path expected = dir.resolve(draft.id.trim() + ".json");

        String json = TestCaseJson.toJson(draft);

        if (samePath(file, expected)) {
            writeUtf8(file, json);
            return file;
        }

        // if expected exists and is different file -> do not overwrite silently
        // instead pick a fresh id (stable) and write there
        if (Files.exists(expected)) {
            String old = draft.id;
            int guard = 0;
            do {
                draft.id = TestCaseDraft.newStableId();
                expected = dir.resolve(draft.id.trim() + ".json");
                guard++;
            } while (Files.exists(expected) && guard < 1000);

            // keep createdAt from old draft; meta will update savedAt anyway
            System.out.println("[FileTestCaseRepository] id collision: " + old + " -> " + draft.id);
            json = TestCaseJson.toJson(draft);
        }

        writeUtf8(expected, json);

        // try delete legacy file if it exists and differs
        try {
            if (Files.exists(file)) Files.delete(file);
        } catch (Exception ignored) {}

        return expected;
    }

    // =====================================================================

    private static Path fileById(String id) {
        String safe = id == null ? "" : id.trim();
        if (safe.isEmpty()) safe = "unknown";
        return ROOT.resolve(safe + ".json");
    }

    private static void ensureStableId(TestCaseDraft d) {
        if (d.id == null) d.id = "";
        d.id = d.id.trim();

        if (d.id.isEmpty()) {
            d.id = TestCaseDraft.newStableId();
        }
    }

    private static boolean samePath(Path a, Path b) {
        if (a == null || b == null) return false;
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (Exception ignore) {
            return a.normalize().toAbsolutePath().equals(b.normalize().toAbsolutePath());
        }
    }

    private static void ensureDir(Path dir) {
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dir: " + dir + " -> " + e.getMessage(), e);
        }
    }

    private static void writeUtf8(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write: " + file + " -> " + e.getMessage(), e);
        }
    }

    // ===================== READ (as in your current) =====================

    public TestCaseDraft readDraft(Path file) {
        // delegate to existing reader to keep behavior consistent
        TestCaseDraft d = TestCaseCardJsonReader.readDraft(file);
        if (d == null) d = new TestCaseDraft();
        return d;
    }
}
