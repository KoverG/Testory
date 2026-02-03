// FILE: src/main/java/app/domain/cycles/repo/FileCycleRepository.java
package app.domain.cycles.repo;

import app.domain.cycles.usecase.CycleDraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileCycleRepository implements CycleRepository {

    // источник истины для списка слева (LeftPaneCoordinator читает отсюда)
    private static final Path ROOT = Path.of("test_resources", "cycles");

    @Override
    public Path rootDir() {
        return ROOT;
    }

    @Override
    public Path saveNew(CycleDraft draft) {
        if (draft == null) draft = new CycleDraft();

        ensureDir(ROOT);

        // stable id / collision guard
        if (draft.id == null) draft.id = "";
        draft.id = draft.id.trim();
        if (draft.id.isEmpty()) draft.id = CycleDraft.newStableId();

        Path file = fileById(draft.id);

        int guard = 0;
        while (Files.exists(file) && guard < 1000) {
            draft.id = CycleDraft.newStableId();
            file = fileById(draft.id);
            guard++;
        }

        // timestamps
        if (draft.createdAtIso == null) draft.createdAtIso = "";
        if (draft.createdAtIso.isBlank()) draft.createdAtIso = CycleDraft.nowIso();
        draft.savedAtIso = CycleDraft.nowIso();

        String json = CycleJson.toJson(draft);
        writeUtf8(file, json);

        return file;
    }

    @Override
    public Path saveExisting(Path file, CycleDraft draft) {
        if (file == null) throw new IllegalArgumentException("File is null");
        if (draft == null) draft = new CycleDraft();

        ensureDir(ROOT);

        // id обязателен: если UI не хранит — восстановим из имени файла
        if (draft.id == null) draft.id = "";
        draft.id = draft.id.trim();
        if (draft.id.isEmpty()) {
            draft.id = stableIdFromFile(file);
        }
        if (draft.id.isEmpty()) {
            // fallback (крайний случай)
            draft.id = CycleDraft.newStableId();
        }

        // timestamps
        if (draft.createdAtIso == null) draft.createdAtIso = "";
        if (draft.createdAtIso.isBlank()) draft.createdAtIso = CycleDraft.nowIso();
        draft.savedAtIso = CycleDraft.nowIso();

        // пишем строго в файл текущего цикла: <id>.json
        Path expected = fileById(draft.id);

        String json = CycleJson.toJson(draft);
        writeUtf8(expected, json);

        // если редактировали legacy путь (или другой файл) — пытаемся прибрать старый
        try {
            if (!samePath(file, expected) && Files.exists(file)) {
                Files.delete(file);
            }
        } catch (Exception ignored) {}

        return expected;
    }

    private static String stableIdFromFile(Path file) {
        try {
            String name = file.getFileName() == null ? "" : file.getFileName().toString();
            if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
            return name == null ? "" : name.trim();
        } catch (Exception ignore) {
            return "";
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

    private static Path fileById(String id) {
        String safe = id == null ? "" : id.trim();
        if (safe.isEmpty()) safe = "unknown";
        return ROOT.resolve(safe + ".json");
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
            Files.writeString(file, text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write: " + file + " -> " + e.getMessage(), e);
        }
    }
}
