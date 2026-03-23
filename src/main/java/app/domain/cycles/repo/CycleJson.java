package app.domain.cycles.repo;

import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;

import java.util.List;

public final class CycleJson {

    private static final char NL = (char) 10;
    private static final char BS = (char) 92;

    private CycleJson() {
    }

    public static String toJson(CycleDraft d) {
        if (d == null) d = new CycleDraft();

        StringBuilder sb = new StringBuilder(896);

        sb.append("{").append(NL);

        sb.append("  \"meta\": {").append(NL);
        sb.append("    \"id\": ").append(q(d.id)).append(",").append(NL);
        sb.append("    \"createdAtIso\": ").append(q(d.createdAtIso)).append(",").append(NL);
        sb.append("    \"savedAtIso\": ").append(q(d.savedAtIso)).append(",").append(NL);
        sb.append("    \"createdAtUi\": ").append(q(d.createdAtUi)).append(NL);
        sb.append("  },").append(NL);

        sb.append("  \"title\": ").append(q(d.title)).append(",").append(NL);
        sb.append("  \"qaResponsible\": ").append(q(d.qaResponsible)).append(",").append(NL);
        sb.append("  \"envType\": ").append(q(d.envType)).append(",").append(NL);
        sb.append("  \"envUrl\": ").append(q(d.envUrl)).append(",").append(NL);
        sb.append("  \"envLinks\": ").append(arrStrings(d.envLinks)).append(",").append(NL);
        sb.append("  \"runState\": ").append(q(CycleRunState.normalize(d.runState))).append(",").append(NL);
        sb.append("  \"runElapsedSeconds\": ").append(Math.max(0L, d.runElapsedSeconds)).append(",").append(NL);
        sb.append("  \"runStartedAtIso\": ").append(q(d.runStartedAtIso)).append(",").append(NL);
        sb.append("  \"cases\": ").append(arrCaseRefs(d.cases)).append(",").append(NL);

        sb.append("  \"recommendation\": ").append(q(d.recommendation)).append(",").append(NL);

        sb.append("  \"taskLink\": {").append(NL);
        sb.append("    \"title\": ").append(q(d.taskLinkTitle)).append(",").append(NL);
        sb.append("    \"url\": ").append(q(d.taskLinkUrl)).append(NL);
        sb.append("  }").append(NL);

        sb.append("}").append(NL);
        return sb.toString();
    }

    private static String q(String raw) {
        if (raw == null) raw = "";
        StringBuilder out = new StringBuilder(raw.length() + 8);
        out.append('"');

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') out.append(BS).append('"');
            else if (c == BS) out.append(BS).append(BS);
            else if (c == '\n' || c == '\r' || c == '\t') out.append(' ');
            else out.append(c);
        }

        out.append('"');
        return out.toString();
    }

    private static String arrStrings(List<String> xs) {
        if (xs == null || xs.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder(xs.size() * 24 + 8);
        sb.append("[");

        int emitted = 0;
        for (String v : xs) {
            if (v == null) continue;
            String s = v.trim();
            if (s.isEmpty()) continue;

            if (emitted > 0) sb.append(", ");
            sb.append(q(s));
            emitted++;
        }

        sb.append("]");
        return sb.toString();
    }

    private static String arrCaseRefs(List<CycleCaseRef> xs) {
        if (xs == null || xs.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder(xs.size() * 88 + 16);
        sb.append("[");

        int emitted = 0;
        int number = 1;

        for (CycleCaseRef ref : xs) {
            if (ref == null) continue;

            String id = ref.safeId();
            if (id.isEmpty()) continue;

            String title = ref.safeTitleSnapshot();
            String status = ref.safeStatus();
            String comment = ref.safeComment();

            if (emitted > 0) {
                sb.append(",").append(NL).append("    ");
            }

            sb.append("{");
            sb.append("\"number\": ").append(number).append(", ");
            sb.append("\"id\": ").append(q(id)).append(", ");
            sb.append("\"title\": ").append(q(title)).append(", ");
            sb.append("\"status\": ").append(q(status)).append(", ");
            sb.append("\"comment\": ").append(q(comment));
            sb.append("}");

            emitted++;
            number++;
        }

        sb.append("]");
        return sb.toString();
    }
}