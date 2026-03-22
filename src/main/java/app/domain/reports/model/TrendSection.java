package app.domain.reports.model;

import java.util.List;

/**
 * Тренд: агрегированные капсулы по 3 цветовым группам.
 * Группы: PASSED (green) / PASSED_WITH_BUGS (yellow) / FAILED+CRITICAL (red).
 * Капсули, у которых count==0, провайдер не добавляет.
 */
public record TrendSection(
        List<TrendCapsule> capsules
) implements ReportSection {

    public static final String TYPE = "TREND";

    @Override
    public String sectionType() { return TYPE; }

    /**
     * @param colorKey "passed" | "bugs" | "failed"
     * @param count    количество прохождений в этой группе
     * @param wide     true = вытянутая капсула, false = круглая
     */
    public record TrendCapsule(
            String colorKey,
            int count,
            boolean wide
    ) {}
}
