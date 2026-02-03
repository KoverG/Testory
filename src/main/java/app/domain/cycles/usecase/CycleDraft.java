package app.domain.cycles.usecase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draft цикла для сохранения на диск.
 * Подход как в TestCases: file name = stable id.
 */
public final class CycleDraft {

    public String id = "";

    // timestamps (ISO)
    public String createdAtIso = "";
    public String savedAtIso = "";

    // UI-created (dd.MM.yyyy), сохраняем как есть (пока)
    public String createdAtUi = "";

    public String title = "";

    // ✅ QA responsible (profile modal)
    public String qaResponsible = "";

    // ✅ task link (service-agnostic)
    public String taskLinkTitle = "";
    public String taskLinkUrl = "";

    // ✅ environment (Environment modal)
    // envType: "desktop" | "mobile" | "" (empty means not set)
    public String envType = "";
    // envUrl: поле "Builds"
    public String envUrl = "";
    // envLinks: chips links
    public List<String> envLinks = new ArrayList<>();

    // ✅ cases: refs (id + snapshot title)
    public List<CycleCaseRef> cases = new ArrayList<>();

    // cy_YYYYMMDDHHMMSSmmm_RRR
    private static final DateTimeFormatter ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public static String nowIso() {
        return String.valueOf(LocalDateTime.now());
    }

    public static String newStableId() {
        String ts = LocalDateTime.now().format(ID_FMT);
        int r = ThreadLocalRandom.current().nextInt(0, 1000);
        return "cy_" + ts + "_" + String.format("%03d", r);
    }

    public static CycleDraft newWithId() {
        CycleDraft d = new CycleDraft();
        d.id = newStableId();
        d.createdAtIso = nowIso();
        return d;
    }
}
