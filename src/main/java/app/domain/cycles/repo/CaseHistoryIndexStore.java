package app.domain.cycles.repo;

import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CaseHistoryIndexStore {

    public record CycleHistoryEntry(
            String cycleId,
            String cycleTitle,
            String createdAtUi,
            String status,
            String comment
    ) {
    }

    private static final Path INDEX_FILE = Path.of("config", "case-history-index.json");
    private static final Path CYCLES_ROOT = Path.of("test_resources", "cycles");
    private static final char Q = '"';
    private static final char BS = '\\';
    private static final char NL = '\n';

    public List<CycleHistoryEntry> read(String caseId) {
        String normalizedCaseId = safe(caseId);
        if (normalizedCaseId.isEmpty()) return List.of();

        Map<String, List<CycleHistoryEntry>> all = readAllEnsuringIndex();
        List<CycleHistoryEntry> entries = all.get(normalizedCaseId);
        if (entries == null || entries.isEmpty()) return List.of();
        return List.copyOf(entries);
    }

    public void upsertCycle(CycleDraft draft) {
        if (draft == null) return;

        String cycleId = safe(draft.id);
        if (cycleId.isEmpty()) return;

        Map<String, List<CycleHistoryEntry>> all = readAllEnsuringIndex();
        removeCycleEntries(all, cycleId);
        appendCycleEntries(all, draft);
        writeIndex(all);
    }

    public void removeCycle(String cycleId) {
        String normalizedCycleId = safe(cycleId);
        if (normalizedCycleId.isEmpty()) return;

        Map<String, List<CycleHistoryEntry>> all = readAllEnsuringIndex();
        removeCycleEntries(all, normalizedCycleId);
        writeIndex(all);
    }

    public void rebuild() {
        writeIndex(buildFromCycles());
    }

    private Map<String, List<CycleHistoryEntry>> readAllEnsuringIndex() {
        if (!Files.exists(INDEX_FILE)) {
            Map<String, List<CycleHistoryEntry>> rebuilt = buildFromCycles();
            writeIndex(rebuilt);
            return rebuilt;
        }

        try {
            return parseIndex(Files.readString(INDEX_FILE, StandardCharsets.UTF_8));
        } catch (Exception ignore) {
            Map<String, List<CycleHistoryEntry>> rebuilt = buildFromCycles();
            writeIndex(rebuilt);
            return rebuilt;
        }
    }

    private Map<String, List<CycleHistoryEntry>> buildFromCycles() {
        Map<String, List<CycleHistoryEntry>> all = new LinkedHashMap<>();
        if (!Files.isDirectory(CYCLES_ROOT)) return all;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(CYCLES_ROOT, "*.json")) {
            for (Path file : ds) {
                if (file == null || !Files.isRegularFile(file)) continue;
                appendCycleEntries(all, CycleCardJsonReader.readDraft(file));
            }
        } catch (IOException ignore) {
            return new LinkedHashMap<>();
        }

        sortEntries(all);
        return all;
    }

    private void appendCycleEntries(Map<String, List<CycleHistoryEntry>> all, CycleDraft draft) {
        if (all == null || draft == null || draft.cases == null || draft.cases.isEmpty()) return;

        String cycleId = safe(draft.id);
        if (cycleId.isEmpty()) return;

        String cycleTitle = safe(draft.title);
        String createdAtUi = safe(draft.createdAtUi);

        for (CycleCaseRef ref : draft.cases) {
            if (ref == null) continue;

            String caseId = ref.safeId();
            if (caseId.isEmpty()) continue;

            List<CycleHistoryEntry> entries = all.computeIfAbsent(caseId, key -> new ArrayList<>());
            entries.add(new CycleHistoryEntry(
                    cycleId,
                    cycleTitle,
                    createdAtUi,
                    ref.safeStatus(),
                    ref.safeComment()
            ));
        }
    }

    private void removeCycleEntries(Map<String, List<CycleHistoryEntry>> all, String cycleId) {
        if (all == null || all.isEmpty()) return;

        List<String> emptyCaseIds = new ArrayList<>();
        for (Map.Entry<String, List<CycleHistoryEntry>> entry : all.entrySet()) {
            List<CycleHistoryEntry> entries = entry.getValue();
            if (entries == null) {
                emptyCaseIds.add(entry.getKey());
                continue;
            }

            entries.removeIf(item -> item == null || cycleId.equals(safe(item.cycleId())));
            if (entries.isEmpty()) emptyCaseIds.add(entry.getKey());
        }

        for (String caseId : emptyCaseIds) {
            all.remove(caseId);
        }
    }

    private void writeIndex(Map<String, List<CycleHistoryEntry>> all) {
        if (all == null) all = new LinkedHashMap<>();
        sortEntries(all);

        try {
            Path parent = INDEX_FILE.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            Files.writeString(INDEX_FILE, toJson(all), StandardCharsets.UTF_8);
        } catch (IOException ignore) {
        }
    }

    private void sortEntries(Map<String, List<CycleHistoryEntry>> all) {
        if (all == null || all.isEmpty()) return;

        Comparator<CycleHistoryEntry> comparator = Comparator
                .comparing((CycleHistoryEntry entry) -> safe(entry.cycleId()).toLowerCase())
                .thenComparing(entry -> safe(entry.cycleTitle()).toLowerCase());

        for (List<CycleHistoryEntry> entries : all.values()) {
            if (entries == null) continue;
            entries.sort(comparator);
        }
    }

    private String toJson(Map<String, List<CycleHistoryEntry>> all) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{").append(NL);
        sb.append("  \"formatVersion\": 1,").append(NL);
        sb.append("  \"cases\": {");

        int caseIndex = 0;
        for (Map.Entry<String, List<CycleHistoryEntry>> entry : all.entrySet()) {
            String caseId = safe(entry.getKey());
            if (caseId.isEmpty()) continue;

            if (caseIndex == 0) sb.append(NL);
            else sb.append(",").append(NL);

            sb.append("    ").append(q(caseId)).append(": [");

            List<CycleHistoryEntry> items = entry.getValue();
            if (items != null && !items.isEmpty()) {
                boolean emittedAny = false;
                for (CycleHistoryEntry item : items) {
                    if (item == null) continue;
                    if (!emittedAny) sb.append(NL);
                    else sb.append(",").append(NL);

                    sb.append("      {").append(NL);
                    sb.append("        \"cycleId\": ").append(q(item.cycleId())).append(",").append(NL);
                    sb.append("        \"cycleTitle\": ").append(q(item.cycleTitle())).append(",").append(NL);
                    sb.append("        \"createdAtUi\": ").append(q(item.createdAtUi())).append(",").append(NL);
                    sb.append("        \"status\": ").append(q(item.status())).append(",").append(NL);
                    sb.append("        \"comment\": ").append(q(item.comment())).append(NL);
                    sb.append("      }");
                    emittedAny = true;
                }
                if (emittedAny) sb.append(NL);
                sb.append("    ]");
            } else {
                sb.append("]");
            }

            caseIndex++;
        }

        if (caseIndex > 0) sb.append(NL);
        sb.append("  }").append(NL);
        sb.append("}").append(NL);
        return sb.toString();
    }

    private Map<String, List<CycleHistoryEntry>> parseIndex(String json) {
        Map<String, List<CycleHistoryEntry>> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return out;

        int casesKey = json.indexOf(Q + "cases" + Q);
        if (casesKey < 0) return out;

        int colon = json.indexOf(':', casesKey);
        if (colon < 0) return out;

        int objStart = skipWs(json, colon + 1);
        if (objStart >= json.length() || json.charAt(objStart) != '{') return out;

        int objEnd = findObjectEnd(json, objStart);
        if (objEnd <= objStart) return out;

        int i = objStart + 1;
        while (i < objEnd) {
            i = skipWs(json, i);
            if (i >= objEnd || json.charAt(i) == '}') break;
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }
            if (json.charAt(i) != Q) {
                i++;
                continue;
            }

            String caseId = readQuoted(json, i);
            if (caseId == null) break;
            i = skipString(json, i);
            i = skipWs(json, i);
            if (i >= objEnd || json.charAt(i) != ':') break;
            i = skipWs(json, i + 1);
            if (i >= objEnd || json.charAt(i) != '[') break;

            int arrEnd = findArrayEnd(json, i);
            if (arrEnd <= i) break;

            List<CycleHistoryEntry> entries = parseEntries(json.substring(i, arrEnd + 1));
            if (!entries.isEmpty()) out.put(caseId, entries);
            i = arrEnd + 1;
        }

        sortEntries(out);
        return out;
    }

    private List<CycleHistoryEntry> parseEntries(String json) {
        List<CycleHistoryEntry> entries = new ArrayList<>();
        if (json == null || json.isBlank()) return entries;

        int i = 1;
        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }
            if (json.charAt(i) != '{') {
                i++;
                continue;
            }

            int objEnd = findObjectEnd(json, i);
            if (objEnd <= i) break;

            String obj = json.substring(i, objEnd + 1);
            entries.add(new CycleHistoryEntry(
                    readString(obj, "cycleId"),
                    readString(obj, "cycleTitle"),
                    readString(obj, "createdAtUi"),
                    readString(obj, "status"),
                    readString(obj, "comment")
            ));
            i = objEnd + 1;
        }

        return entries;
    }

    private static String readString(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return "";

        int k = json.indexOf(Q + key + Q);
        if (k < 0) return "";

        k = json.indexOf(':', k);
        if (k < 0) return "";

        k = skipWs(json, k + 1);
        if (k >= json.length() || json.charAt(k) != Q) return "";

        String value = readQuoted(json, k);
        return value == null ? "" : value.trim();
    }

    private static int skipString(String json, int quoteStart) {
        boolean esc = false;
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == BS) {
                esc = true;
                continue;
            }
            if (c == Q) return i + 1;
        }
        return json.length();
    }

    private static String readQuoted(String json, int quoteStart) {
        StringBuilder sb = new StringBuilder();
        boolean esc = false;

        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == '"' || c == BS || c == '/') sb.append(c);
                else if (c == 'n' || c == 'r' || c == 't') sb.append(' ');
                else sb.append(c);
                esc = false;
                continue;
            }
            if (c == BS) {
                esc = true;
                continue;
            }
            if (c == Q) return sb.toString();
            sb.append(c);
        }

        return null;
    }

    private static int findObjectEnd(String json, int objStart) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;

        for (int i = objStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == BS) {
                    esc = true;
                    continue;
                }
                if (c == Q) inStr = false;
                continue;
            }

            if (c == Q) {
                inStr = true;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }

        return -1;
    }

    private static int findArrayEnd(String json, int arrStart) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;

        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == BS) {
                    esc = true;
                    continue;
                }
                if (c == Q) inStr = false;
                continue;
            }

            if (c == Q) {
                inStr = true;
                continue;
            }
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }

        return -1;
    }

    private static int skipWs(String json, int i) {
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private static String q(String raw) {
        String value = raw == null ? "" : raw;
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') out.append(BS).append('"');
            else if (c == BS) out.append(BS).append(BS);
            else if (c == '\n' || c == '\r' || c == '\t') out.append(' ');
            else out.append(c);
        }
        out.append('"');
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}