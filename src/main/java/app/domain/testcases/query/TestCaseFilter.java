// FILE: src/main/java/app/domain/testcases/query/TestCaseFilter.java
package app.domain.testcases.query;

import app.domain.testcases.TestCase;

import java.util.List;

public final class TestCaseFilter {

    private TestCaseFilter() {}

    public static boolean matches(
            TestCase tc,
            List<String> labels,
            List<String> tags
    ) {
        if (tc == null) return false;

        if (!matchesLabels(tc, labels)) return false;
        if (!matchesTags(tc, tags)) return false;

        return true;
    }

    private static boolean matchesLabels(TestCase tc, List<String> labels) {
        if (labels == null || labels.isEmpty()) return true;
        return tc.hasAnyLabel(labels);
    }

    private static boolean matchesTags(TestCase tc, List<String> tags) {
        if (tags == null || tags.isEmpty()) return true;
        return tc.hasAnyTag(tags);
    }
}
