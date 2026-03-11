package app.domain.cycles.usecase;

import app.domain.cycles.repo.CycleRepository;

import java.nio.file.Path;

public final class CreateCycleUseCase {

    private final CycleRepository repo;

    public CreateCycleUseCase(CycleRepository repo) {
        this.repo = repo;
    }

    public Path create(CycleDraft draft) {
        if (draft == null) draft = new CycleDraft();

        normalize(draft);

        // meta
        if (draft.id.isEmpty()) draft.id = CycleDraft.newStableId();
        if (draft.createdAtIso.isEmpty()) draft.createdAtIso = CycleDraft.nowIso();

        return repo.saveNew(draft);
    }

    // ✅ update existing cycle (no duplicates)
    public Path update(Path file, CycleDraft draft) {
        if (file == null) throw new IllegalArgumentException("File is null");
        if (draft == null) draft = new CycleDraft();

        normalize(draft);

        if (draft.id.isEmpty()) {
            draft.id = stableIdFromFile(file);
        }
        if (draft.id.isEmpty()) draft.id = CycleDraft.newStableId();

        if (draft.createdAtIso.isEmpty()) draft.createdAtIso = CycleDraft.nowIso();

        return repo.saveExisting(file, draft);
    }

    private static String stableIdFromFile(Path file) {
        try {
            String name = file.getFileName() == null ? "" : file.getFileName().toString();
            if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
            return trim(name);
        } catch (Exception ignore) {
            return "";
        }
    }

    private static void normalize(CycleDraft d) {
        d.id = trim(d.id);
        d.createdAtIso = trim(d.createdAtIso);
        d.savedAtIso = trim(d.savedAtIso);
        d.createdAtUi = trim(d.createdAtUi);
        d.title = trim(d.title);

        // ✅ QA responsible
        d.qaResponsible = trim(d.qaResponsible);

        // ✅ task link
        d.taskLinkTitle = trim(d.taskLinkTitle);
        d.taskLinkUrl = trim(d.taskLinkUrl);

        // ✅ environment
        d.envType = trim(d.envType);
        d.envUrl = trim(d.envUrl);
        if (d.envLinks == null) d.envLinks = java.util.List.of();

        if (d.cases == null) d.cases = java.util.List.of();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
