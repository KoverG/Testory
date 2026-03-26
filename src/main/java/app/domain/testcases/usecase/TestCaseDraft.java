package app.domain.testcases.usecase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TestCaseDraft {

    // stable internal id (never changes)
    public String id = "";

    // meta timestamps (optional, but useful)
    public String createdAt = "";
    public String savedAt = "";

    public String code = "";
    public String number = "";

    public String title = "";
    public String description = "";
    public String taskLinkTitle = "";
    public String taskLinkUrl = "";

    public List<String> labels = new ArrayList<>();
    public List<String> tags = new ArrayList<>();

    public List<StepDraft> steps = new ArrayList<>();

    public static final class StepDraft {
        public String step = "";
        public String data = "";
        public String expected = "";

        public StepDraft() {}

        public StepDraft(String step, String data, String expected) {
            this.step = step == null ? "" : step;
            this.data = data == null ? "" : data;
            this.expected = expected == null ? "" : expected;
        }
    }

    // =====================================================================
    // ID generator (stable, time-based, numeric-friendly for your sorter)
    // tc_YYYYMMDDHHMMSSmmm_RRR
    // digits are inside, so TestCaseSorter.numericId keeps working for NEW/OLD
    // =====================================================================

    private static final DateTimeFormatter ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public static String newStableId() {
        String ts = LocalDateTime.now().format(ID_FMT);

        // small random tail to avoid collisions in the same millisecond
        int r = (int) (Math.random() * 1000.0);
        String rr = String.valueOf(r);
        while (rr.length() < 3) rr = "0" + rr;

        return "tc_" + ts + "_" + rr;
    }

    public static String nowIso() {
        return String.valueOf(LocalDateTime.now());
    }

    public static TestCaseDraft newWithId() {
        TestCaseDraft d = new TestCaseDraft();
        d.id = newStableId();
        d.createdAt = nowIso();
        return d;
    }
}
