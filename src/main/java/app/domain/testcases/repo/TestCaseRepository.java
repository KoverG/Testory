// FILE: src/main/java/app/domain/testcases/repo/TestCaseRepository.java
package app.domain.testcases.repo;

import app.domain.testcases.usecase.TestCaseDraft;

import java.nio.file.Path;

public interface TestCaseRepository {

    Path rootDir();

    Path saveNew(TestCaseDraft draft);

    // overwrite existing file (no dedupe, no rename)
    Path saveExisting(Path file, TestCaseDraft draft);
}
