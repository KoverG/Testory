package app.domain.testcases.usecase;

import app.domain.testcases.repo.TestCaseRepository;

import java.nio.file.Path;

public final class CreateTestCaseUseCase {

    private final TestCaseRepository repo;

    public CreateTestCaseUseCase(TestCaseRepository repo) {
        this.repo = repo;
    }

    public Path create(TestCaseDraft draft) {
        validate(draft);
        ensureMeta(draft);
        return repo.saveNew(draft);
    }

    public Path update(Path file, TestCaseDraft draft) {
        if (file == null) throw new IllegalArgumentException("File is null");
        validate(draft);
        ensureMeta(draft);
        return repo.saveExisting(file, draft);
    }

    private static void ensureMeta(TestCaseDraft d) {
        if (d.id == null) d.id = "";
        d.id = d.id.trim();
        if (d.id.isEmpty()) d.id = TestCaseDraft.newStableId();

        if (d.createdAt == null) d.createdAt = "";
        if (d.createdAt.isBlank()) d.createdAt = TestCaseDraft.nowIso();
    }

    private static void validate(TestCaseDraft d) {
        if (d == null) throw new IllegalArgumentException("Draft is null");

        if (d.code == null) d.code = "";
        if (d.number == null) d.number = "";
        if (d.title == null) d.title = "";
        if (d.description == null) d.description = "";
    }
}
