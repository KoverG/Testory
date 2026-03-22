package app.domain.reports.model;

import java.nio.file.Path;

/**
 * Описывает объект, по которому строится отчёт.
 * Расширяется добавлением новых полей (featureLabel, cycleType, period и т.д.)
 * без изменения существующих провайдеров.
 */
public final class ReportTarget {

    private final ReportTargetType type;
    private final String id;
    private final Path file;   // путь к JSON-файлу цикла (для CYCLE)

    private ReportTarget(ReportTargetType type, String id, Path file) {
        this.type = type;
        this.id   = id == null ? "" : id;
        this.file = file;
    }

    public static ReportTarget forCase(String caseId) {
        return new ReportTarget(ReportTargetType.TEST_CASE, caseId, null);
    }

    public static ReportTarget forCycle(String cycleId, Path cycleFile) {
        return new ReportTarget(ReportTargetType.CYCLE, cycleId, cycleFile);
    }

    public ReportTargetType type()   { return type; }
    public String           id()     { return id;   }
    public Path             file()   { return file; }
}
