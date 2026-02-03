// FILE: src/main/java/app/domain/cycles/repo/CycleRepository.java
package app.domain.cycles.repo;

import app.domain.cycles.usecase.CycleDraft;

import java.nio.file.Path;

public interface CycleRepository {
    Path rootDir();
    Path saveNew(CycleDraft draft);

    // ✅ для редактирования существующего цикла (чтобы save не плодил дубликаты)
    Path saveExisting(Path file, CycleDraft draft);
}
