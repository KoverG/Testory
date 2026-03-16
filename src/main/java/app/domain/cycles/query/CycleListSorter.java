package app.domain.cycles.query;

import app.core.AppSettings;
import app.domain.cycles.ui.list.CycleListItem;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class CycleListSorter {

    public static final int SORT_CREATED_NEWEST = 0;
    public static final int SORT_CREATED_OLDEST = 1;
    public static final int SORT_TITLE_ASC = 2;
    public static final int SORT_TITLE_DESC = 3;
    public static final int SORT_PROGRESS_DESC = 4;
    public static final int SORT_PROGRESS_ASC = 5;
    public static final int SORT_CASE_COUNT_DESC = 6;
    public static final int SORT_CASE_COUNT_ASC = 7;
    public static final int SORT_CRITICAL_COUNT_DESC = 8;
    public static final int SORT_CRITICAL_COUNT_ASC = 9;

    private CycleListSorter() {
    }

    public static void sort(List<CycleListItem> list, int mode, Function<String, Snapshot> snapshotResolver) {
        if (list == null || list.size() <= 1) return;

        Comparator<CycleListItem> createdDesc = Comparator
                .comparing((CycleListItem item) -> snapshot(item, snapshotResolver).createdSortKey(), String.CASE_INSENSITIVE_ORDER)
                .reversed();
        Comparator<CycleListItem> createdAsc = Comparator
                .comparing((CycleListItem item) -> snapshot(item, snapshotResolver).createdSortKey(), String.CASE_INSENSITIVE_ORDER);
        Comparator<CycleListItem> stableId = Comparator.comparing(CycleListItem::id, String.CASE_INSENSITIVE_ORDER);

        Comparator<CycleListItem> cmp;
        switch (mode) {
            case SORT_CREATED_OLDEST:
                cmp = createdAsc.thenComparing(stableId);
                break;
            case SORT_TITLE_ASC:
                cmp = titleCmp(true)
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_TITLE_DESC:
                cmp = titleCmp(false)
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_PROGRESS_DESC:
                cmp = Comparator.comparingDouble((CycleListItem item) -> snapshot(item, snapshotResolver).progressPercent())
                        .reversed()
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_PROGRESS_ASC:
                cmp = Comparator.comparingDouble((CycleListItem item) -> snapshot(item, snapshotResolver).progressPercent())
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_CASE_COUNT_DESC:
                cmp = Comparator.comparingInt((CycleListItem item) -> snapshot(item, snapshotResolver).caseCount())
                        .reversed()
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_CASE_COUNT_ASC:
                cmp = Comparator.comparingInt((CycleListItem item) -> snapshot(item, snapshotResolver).caseCount())
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_CRITICAL_COUNT_DESC:
                cmp = Comparator.comparingInt((CycleListItem item) -> snapshot(item, snapshotResolver).criticalCount())
                        .reversed()
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_CRITICAL_COUNT_ASC:
                cmp = Comparator.comparingInt((CycleListItem item) -> snapshot(item, snapshotResolver).criticalCount())
                        .thenComparing(createdDesc)
                        .thenComparing(stableId);
                break;
            case SORT_CREATED_NEWEST:
            default:
                cmp = createdDesc.thenComparing(stableId);
                break;
        }

        list.sort(cmp);
    }

    private static Comparator<CycleListItem> titleCmp(boolean asc) {
        final String lang = AppSettings.lang();
        final Locale locale = Locale.forLanguageTag(lang == null || lang.isBlank() ? "ru" : lang);
        final Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);

        return (left, right) -> {
            String a = left == null ? "" : left.safeTitle();
            String b = right == null ? "" : right.safeTitle();
            int result = collator.compare(a, b);
            return asc ? result : -result;
        };
    }

    private static Snapshot snapshot(CycleListItem item, Function<String, Snapshot> resolver) {
        if (item == null || resolver == null) return Snapshot.EMPTY;
        Snapshot snapshot = resolver.apply(item.id());
        return snapshot == null ? Snapshot.EMPTY : snapshot;
    }

    public record Snapshot(String createdSortKey, double progressPercent, int caseCount, int criticalCount) {
        public static final Snapshot EMPTY = new Snapshot("", 0.0, 0, 0);
    }
}