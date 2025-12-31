package app.domain.testcases.query;

import app.core.AppSettings;
import app.domain.testcases.TestCase;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TestCaseSorter {

    // IMPORTANT: indices must match TestCasesController.SORT_KEYS order
    public static final int SORT_CREATED_NEWEST = 0;  // createdAt desc
    public static final int SORT_CREATED_OLDEST = 1;  // createdAt asc
    public static final int SORT_SAVED_RECENT = 2;    // savedAt desc

    public static final int SORT_CODE = 3;            // code asc, number asc
    public static final int SORT_NUMBER_ASC = 4;      // code asc, number asc
    public static final int SORT_NUMBER_DESC = 5;     // code asc, number desc

    public static final int SORT_TITLE_ASC = 6;       // code asc, title asc (lang-priority)
    public static final int SORT_TITLE_DESC = 7;      // code asc, title desc (lang-priority)

    private TestCaseSorter() {}

    public static void sort(List<TestCase> list, int mode) {
        if (list == null || list.size() <= 1) return;

        Comparator<TestCase> cmp;

        switch (mode) {
            case SORT_CREATED_OLDEST:
                cmp = createdAtCmpAsc()
                        .thenComparing(TestCaseSorter::savedAtKey, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;

            case SORT_SAVED_RECENT:
                cmp = savedAtCmpDesc()
                        .thenComparing(createdAtCmpDesc())
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;

            case SORT_CODE:
            case SORT_NUMBER_ASC:
                cmp = codeCmpAsc()
                        .thenComparingLong(TestCaseSorter::numberKeyAsc)
                        .thenComparing(createdAtCmpDesc())
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;

            case SORT_NUMBER_DESC:
                cmp = codeCmpAsc()
                        .thenComparingLong(TestCaseSorter::numberKeyDesc)
                        .thenComparing(createdAtCmpDesc())
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;

            case SORT_TITLE_DESC:
                cmp = codeCmpAsc()
                        .thenComparing(titleCmp(false))
                        .thenComparingLong(TestCaseSorter::numberKeyAsc)
                        .thenComparing(createdAtCmpDesc())
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;

            case SORT_TITLE_ASC:
                cmp = codeCmpAsc()
                        .thenComparing(titleCmp(true))
                        .thenComparingLong(TestCaseSorter::numberKeyAsc)
                        .thenComparing(createdAtCmpDesc())
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;

            case SORT_CREATED_NEWEST:
            default:
                cmp = createdAtCmpDesc()
                        .thenComparing(savedAtCmpDesc())
                        .thenComparingLong(TestCaseSorter::numericId)
                        .thenComparing(TestCaseSorter::safeId, String.CASE_INSENSITIVE_ORDER);
                break;
        }

        list.sort(cmp);
    }

    // =====================================================================
    // Comparators / keys
    // =====================================================================

    private static Comparator<TestCase> codeCmpAsc() {
        return Comparator.comparing(
                (TestCase tc) -> safe(tc == null ? null : tc.getCode()),
                String.CASE_INSENSITIVE_ORDER
        );
    }

    private static Comparator<TestCase> createdAtCmpDesc() {
        return Comparator.<TestCase, String>comparing(TestCaseSorter::createdAtKey, String.CASE_INSENSITIVE_ORDER).reversed();
    }

    private static Comparator<TestCase> createdAtCmpAsc() {
        return Comparator.comparing(TestCaseSorter::createdAtKey, String.CASE_INSENSITIVE_ORDER);
    }

    private static Comparator<TestCase> savedAtCmpDesc() {
        return Comparator.<TestCase, String>comparing(TestCaseSorter::savedAtKey, String.CASE_INSENSITIVE_ORDER).reversed();
    }

    private static Comparator<TestCase> titleCmp(boolean asc) {
        final String lang = AppSettings.lang();
        final Locale loc = Locale.forLanguageTag(lang == null || lang.isBlank() ? "ru" : lang);

        final Collator collator = Collator.getInstance(loc);
        collator.setStrength(Collator.PRIMARY);

        return (a, b) -> {
            String ta = safe(a == null ? null : a.getTitle());
            String tb = safe(b == null ? null : b.getTitle());

            int ba = langBucket(ta, lang);
            int bb = langBucket(tb, lang);
            if (ba != bb) return Integer.compare(ba, bb);

            int c = collator.compare(ta, tb);
            if (!asc) c = -c;
            return c;
        };
    }

    /**
     * Приоритет языка из AppSettings.lang():
     * - ru: сначала заголовки, начинающиеся на кириллицу, потом остальные
     * - en: сначала заголовки, начинающиеся на латиницу, потом остальные
     */
    private static int langBucket(String title, String lang) {
        if (title == null) return 1;
        String t = title.trim();
        if (t.isEmpty()) return 1;

        char ch = t.charAt(0);

        if ("ru".equalsIgnoreCase(lang)) {
            return isCyrillic(ch) ? 0 : 1;
        }
        if ("en".equalsIgnoreCase(lang)) {
            return isLatin(ch) ? 0 : 1;
        }

        // default: no special priority
        return 0;
    }

    private static boolean isCyrillic(char ch) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
        return b == Character.UnicodeBlock.CYRILLIC
                || b == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                || b == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                || b == Character.UnicodeBlock.CYRILLIC_EXTENDED_B;
    }

    private static boolean isLatin(char ch) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
        return b == Character.UnicodeBlock.BASIC_LATIN
                || b == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || b == Character.UnicodeBlock.LATIN_EXTENDED_A
                || b == Character.UnicodeBlock.LATIN_EXTENDED_B
                || b == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL;
    }

    private static String createdAtKey(TestCase tc) {
        if (tc == null) return "";
        String s = tc.getCreatedAt();
        if (s == null) return "";
        // ISO-8601 строка лексикографически сортируется как время
        return s.isBlank() ? "" : s;
    }

    private static String savedAtKey(TestCase tc) {
        if (tc == null) return "";
        String s = tc.getSavedAt();
        if (s == null) return "";
        return s.isBlank() ? "" : s;
    }

    private static long numberKeyAsc(TestCase tc) {
        return numberAsLong(tc, Long.MAX_VALUE); // пустые/мусор -> в конец
    }

    private static long numberKeyDesc(TestCase tc) {
        long v = numberAsLong(tc, Long.MIN_VALUE);
        // DESC: инвертируем. Пустые => Long.MIN_VALUE => после "-" станут Long.MAX_VALUE (в конец)
        return -v;
    }

    private static long numberAsLong(TestCase tc, long emptyValue) {
        if (tc == null) return emptyValue;
        String n = tc.getNumber();
        if (n == null) return emptyValue;
        n = n.trim();
        if (n.isEmpty()) return emptyValue;

        // только цифры (у тебя число концептуально numeric)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        if (sb.length() == 0) return emptyValue;

        try {
            return Long.parseLong(sb.toString());
        } catch (NumberFormatException e) {
            return emptyValue;
        }
    }

    private static String safeId(TestCase tc) {
        if (tc == null) return "";
        return safe(tc.getId());
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.trim();
    }

    /**
     * Fallback tiebreaker:
     * tc_YYYYMMDDHHMMSSmmm_RRR => digits can be extracted for stable ordering.
     */
    private static long numericId(TestCase tc) {
        if (tc == null) return 0L;
        String id = tc.getId();
        if (id == null) return 0L;

        long out = 0L;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') continue;

            int d = c - '0';
            out = out * 10L + d;

            // prevent overflow runaway (not critical)
            if (out > 9_000_000_000_000_000_000L) break;
        }
        return out;
    }
}
