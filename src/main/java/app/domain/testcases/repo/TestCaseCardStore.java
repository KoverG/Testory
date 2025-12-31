package app.domain.testcases.repo;

import app.domain.testcases.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TestCaseCardStore {

    private static final Path ROOT = Path.of("test_resources", "test_cases");

    private TestCaseCardStore() {}

    public static List<TestCase> loadAll() {
        ensureDir(ROOT);

        List<TestCase> out = new ArrayList<>();

        try (Stream<Path> s = Files.list(ROOT)) {
            s.filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> {
                        TestCase tc = TestCaseCardJsonReader.read(p);
                        if (tc != null) out.add(tc);
                    });
        } catch (IOException ignored) {}

        return out;
    }

    public static Path fileOf(String id) {
        ensureDir(ROOT);
        String safe = (id == null) ? "" : id.trim();
        if (safe.isEmpty()) return ROOT.resolve("unknown.json");
        return ROOT.resolve(safe + ".json");
    }

    private static void ensureDir(Path dir) {
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException ignored) {}
    }
}
