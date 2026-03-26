package app.domain.testcases.repo;

import app.domain.testcases.usecase.TestCaseDraft;

import java.time.LocalDateTime;
import java.util.List;

public final class TestCaseJson {

    private static final char NL = (char) 10;
    private static final char BS = (char) 92;

    private TestCaseJson() {}

    public static String toJson(TestCaseDraft d) {
        if (d == null) d = new TestCaseDraft();

        String now = String.valueOf(LocalDateTime.now());

        if (d.id == null) d.id = "";
        if (d.createdAt == null) d.createdAt = "";
        if (d.savedAt == null) d.savedAt = "";

        if (d.createdAt.isBlank()) d.createdAt = now;
        d.savedAt = now;

        StringBuilder sb = new StringBuilder(1024);

        sb.append("{").append(NL);

        sb.append("  \"meta\": {").append(NL);
        sb.append("    \"id\": ").append(q(d.id)).append(",").append(NL);
        sb.append("    \"createdAt\": ").append(q(d.createdAt)).append(",").append(NL);
        sb.append("    \"savedAt\": ").append(q(d.savedAt)).append(NL);
        sb.append("  },").append(NL);

        sb.append("  \"code\": ").append(q(d.code)).append(",").append(NL);
        sb.append("  \"number\": ").append(q(d.number)).append(",").append(NL);
        sb.append("  \"title\": ").append(q(d.title)).append(",").append(NL);
        sb.append("  \"description\": ").append(q(d.description)).append(",").append(NL);
        sb.append("  \"taskLinkTitle\": ").append(q(d.taskLinkTitle)).append(",").append(NL);
        sb.append("  \"taskLinkUrl\": ").append(q(d.taskLinkUrl)).append(",").append(NL);

        sb.append("  \"labels\": ").append(arr(d.labels)).append(",").append(NL);
        sb.append("  \"tags\": ").append(arr(d.tags)).append(",").append(NL);

        sb.append("  \"steps\": [").append(NL);

        List<TestCaseDraft.StepDraft> steps = d.steps;
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                TestCaseDraft.StepDraft st = steps.get(i);
                if (st == null) st = new TestCaseDraft.StepDraft();

                sb.append("    {").append(NL);
                sb.append("      \"n\": ").append(i + 1).append(",").append(NL);
                sb.append("      \"step\": ").append(q(st.step)).append(",").append(NL);
                sb.append("      \"data\": ").append(q(st.data)).append(",").append(NL);
                sb.append("      \"expected\": ").append(q(st.expected)).append(NL);
                sb.append("    }");

                if (i < steps.size() - 1) sb.append(",");
                sb.append(NL);
            }
        }

        sb.append("  ]").append(NL);
        sb.append("}").append(NL);

        return sb.toString();
    }

    private static String arr(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < items.size(); i++) {
            sb.append(q(items.get(i)));
            if (i < items.size() - 1) sb.append(", ");
        }

        sb.append("]");
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) s = "";

        StringBuilder out = new StringBuilder();
        out.append('"');

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"') out.append(BS).append('"');
            else if (c == (char) 10) out.append(BS).append('n');
            else if (c == (char) 13) out.append(BS).append('r');
            else if (c == (char) 9) out.append(BS).append('t');
            else out.append(c);
        }

        out.append('"');
        return out.toString();
    }
}
