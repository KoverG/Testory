// FILE: src/main/java/app/domain/testcases/query/TestCaseQuery.java
package app.domain.testcases.query;

import app.domain.testcases.TestCase;

import java.util.ArrayList;
import java.util.List;

public final class TestCaseQuery {

    private TestCaseQuery() {}

    public static List<TestCase> apply(
            List<TestCase> source,
            List<String> labels,
            List<String> tags,
            int sortMode
    ) {
        List<TestCase> out = new ArrayList<>();

        if (source == null) return out;

        for (TestCase tc : source) {
            if (TestCaseFilter.matches(tc, labels, tags)) {
                out.add(tc);
            }
        }

        TestCaseSorter.sort(out, sortMode);
        return out;
    }
}
