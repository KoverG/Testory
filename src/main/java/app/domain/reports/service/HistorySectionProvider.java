package app.domain.reports.service;
import app.domain.cycles.repo.CaseHistoryIndexStore;
import app.domain.cycles.repo.CycleCardJsonReader;
import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.reports.model.HistorySection;
import app.domain.reports.model.HistorySection.HistoryRow;
import app.domain.reports.model.ReportSection;
import app.domain.reports.model.ReportTarget;
import app.domain.reports.model.ReportTargetType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
public final class HistorySectionProvider implements ReportSectionProvider {
    private static final DateTimeFormatter DISP_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final CaseHistoryIndexStore historyStore;
    public HistorySectionProvider(CaseHistoryIndexStore historyStore) {
        this.historyStore = historyStore;
    }
    private static String formatIso(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            String trimmed = iso.length() > 19 ? iso.substring(0, 19) : iso;
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DISP_FMT);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }
    @Override
    public Optional<ReportSection> provide(ReportTarget target) {
        if (target.type() == ReportTargetType.TEST_CASE) {
            return buildForCase(target);
        } else if (target.type() == ReportTargetType.CYCLE) {
            return buildForCycle(target);
        }
        return Optional.empty();
    }
    private Optional<ReportSection> buildForCase(ReportTarget target) {
        List<CaseHistoryIndexStore.CycleHistoryEntry> entries = historyStore.read(target.id());
        if (entries.isEmpty()) return Optional.empty();
        List<HistoryRow> rows = new ArrayList<>();
        for (var e : entries) {
            rows.add(new HistoryRow(
                    0,
                    e.cycleId(),
                    "",
                    e.status(),
                    e.comment(),
                    e.createdAtUi(),
                    e.cycleTitle().isBlank() ? "Р С›Р Т‘Р С‘Р Р…Р С•РЎвЂЎР Р…РЎвЂ№Р в„–" : e.cycleTitle()
            ));
        }
        return Optional.of(new HistorySection(rows));
    }
    private Optional<ReportSection> buildForCycle(ReportTarget target) {
        if (target.file() == null) return Optional.empty();
        CycleDraft draft = CycleCardJsonReader.readDraft(target.file());
        if (draft == null || draft.cases == null || draft.cases.isEmpty()) return Optional.empty();
        String fallbackDisplay = formatIso(draft.savedAtIso);
        if (fallbackDisplay.isBlank()) fallbackDisplay = draft.createdAtUi != null ? draft.createdAtUi : "";
        List<HistoryRow> rows = new ArrayList<>();
        int ordinal = 1;
        for (CycleCaseRef ref : draft.cases) {
            String title = ref.safeTitleSnapshot().isBlank() ? ref.safeId() : ref.safeTitleSnapshot();
            String changedAtDisplay = formatIso(ref.safeStatusChangedAtIso());
            if (changedAtDisplay.isBlank()) changedAtDisplay = fallbackDisplay;
            rows.add(new HistoryRow(
                    ordinal++,
                    ref.safeId(),
                    title,
                    ref.safeStatus(),
                    ref.safeComment(),
                    changedAtDisplay,
                    ""
            ));
        }
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(new HistorySection(rows));
    }
}
