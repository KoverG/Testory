package app.domain.testcases.usecase;

import app.domain.testcases.repo.TestCaseRepository;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Backward-compatible wrapper.
 * Реальная логика сохранения живёт в CreateTestCaseUseCase и репозитории.
 */
public final class SaveTestCaseUseCase {

    private final CreateTestCaseUseCase create;

    public SaveTestCaseUseCase(TestCaseRepository repo) {
        this.create = new CreateTestCaseUseCase(repo);
    }

    public Path execute(TestCaseDraft draft) throws IOException {
        return create.create(draft);
    }
}
