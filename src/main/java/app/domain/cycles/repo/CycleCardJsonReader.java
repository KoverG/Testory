package app.domain.cycles.repo;

import app.domain.cycles.usecase.CycleCaseRef;
import app.domain.cycles.usecase.CycleDraft;
import app.domain.cycles.usecase.CycleRunState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads cycle card metadata for the list and the full cycle draft for the right panel.
 * Works without external JSON libraries.
 */
public final class CycleCardJsonReader {

    private static final char Q = '"';
    private static final char BS = '\\';
    private static final char NL = '\n';
    private static final char CR = '\r';
    private static final char TAB = '\t';

    private CycleCardJsonReader() {}

    public static final class CycleListMeta {
        public final String title;
        public final String createdAtUi;

        public CycleListMeta(String title, String createdAtUi) {
            this.title = title == null ? "" : title;
            this.createdAtUi = createdAtUi == null ? "" : createdAtUi;
        }
    }

    public static CycleListMeta readListMeta(Path file) {
        if (file == null) return null;

        final String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        String createdAtUi = readMetaString(json, "createdAtUi");
        String title = readRootTitleAfterMeta(json);
        return new CycleListMeta(title, createdAtUi);
    }

    public static CycleDraft readDraft(Path file) {
        if (file == null) return null;

        final String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        CycleDraft d = new CycleDraft();

        d.id = readMetaString(json, "id");
        d.createdAtIso = readMetaString(json, "createdAtIso");
        d.savedAtIso = readMetaString(json, "savedAtIso");
        d.createdAtUi = readMetaString(json, "createdAtUi");

        d.title = readRootTitleAfterMeta(json);
        d.qaResponsible = readRootStringAfterMeta(json, "qaResponsible");
        d.envType = readRootStringAfterMeta(json, "envType");
        d.envUrl = readRootStringAfterMeta(json, "envUrl");
        d.envLinks = readRootStringArrayAfterMeta(json, "envLinks");
        d.runState = CycleRunState.normalize(readRootStringAfterMeta(json, "runState"));
        d.runElapsedSeconds = readRootLongAfterMeta(json, "runElapsedSeconds");
        d.runStartedAtIso = readRootStringAfterMeta(json, "runStartedAtIso");
        d.cases = readCaseRefsAfterMeta(json);
        d.taskLinkTitle = readTaskLinkStringAfterMeta(json, "title");
        d.taskLinkUrl = readTaskLinkStringAfterMeta(json, "url");
        d.recommendation = readRootStringAfterMeta(json, "recommendation");

        return d;
    }

    private static String readRootTitleAfterMeta(String json) {
        if (json == null || json.isBlank()) return "";
        return readStringFrom(json, "title", findRootSearchStart(json));
    }

    private static String readRootStringAfterMeta(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return "";
        return readStringFrom(json, key, findRootSearchStart(json));
    }

    private static long readRootLongAfterMeta(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return 0L;
        return readLongFrom(json, key, findRootSearchStart(json));
    }

    private static List<String> readRootStringArrayAfterMeta(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) return new ArrayList<>();
        return readStringArrayFrom(json, key, findRootSearchStart(json));
    }

    private static int findRootSearchStart(String json) {
        int metaKey = json.indexOf(Q + "meta" + Q);
        int from = 0;
        if (metaKey >= 0) {
            int objStart = json.indexOf('{', metaKey);
            if (objStart >= 0) {
                int objEnd = findObjectEnd(json, objStart);
                if (objEnd > objStart) {
                    from = objEnd + 1;
                }
            }
        }
        return from;
    }

    private static List<CycleCaseRef> readCaseRefsAfterMeta(String json) {
        List<CycleCaseRef> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;

        int k = json.indexOf(Q + "cases" + Q, findRootSearchStart(json));
        if (k < 0) return out;

        k = json.indexOf(':', k);
        if (k < 0) return out;

        k++;
        k = skipWs(json, k);
        if (k >= json.length() || json.charAt(k) != '[') return out;

        int i = k + 1;
        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length()) break;

            char c = json.charAt(i);
            if (c == ']') break;
            if (c == ',') {
                i++;
                continue;
            }
            if (c != '{') {
                i++;
                continue;
            }

            int objEnd = findObjectEnd(json, i);
            if (objEnd <= i) break;

            String obj = json.substring(i, objEnd + 1);
            String id = readString(obj, "id");
            String title = readString(obj, "title");
            String status = readString(obj, "status");
            String comment = readString(obj, "comment");
            String statusChangedAtIso = readString(obj, "statusChangedAtIso");

            if (id != null && !id.isBlank()) {
                out.add(new CycleCaseRef(
                        id.trim(),
                        title == null ? "" : title.trim(),
                        status == null ? "" : status.trim(),
                        comment == null ? "" : comment.trim(),
                        statusChangedAtIso == null ? "" : statusChangedAtIso.trim()
                ));
            }

            i = objEnd + 1;
        }

        return out;
    }

    private static String readTaskLinkStringAfterMeta(String json, String key) {
        if (json == null || json.isBlank()) return "";

        int k = json.indexOf(Q + "taskLink" + Q, findRootSearchStart(json));
        if (k < 0) return "";

        k = json.indexOf(':', k);
        if (k < 0) return "";

        k++;
        k = skipWs(json, k);
        if (k >= json.length() || json.charAt(k) != '{') return "";

        int objEnd = findObjectEnd(json, k);
        if (objEnd <= k) return "";

        String obj = json.substring(k, objEnd + 1);
        return readString(obj, key);
    }

    private static String readMetaString(String json, String key) {
        if (json == null || key == null) return "";

        int m = json.indexOf(Q + "meta" + Q);
        if (m < 0) return "";

        m = json.indexOf('{', m);
        if (m < 0) return "";

        int mend = findObjectEnd(json, m);
        if (mend <= m) return "";

        String metaObj = json.substring(m, mend + 1);
        return readString(metaObj, key);
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

    private static List<String> readStringArrayFrom(String json, String key, int fromIndex) {
        List<String> out = new ArrayList<>();
        if (json == null || key == null) return out;
        if (fromIndex < 0) fromIndex = 0;
        if (fromIndex >= json.length()) return out;

        int i = json.indexOf(Q + key + Q, fromIndex);
        if (i < 0) return out;

        i = json.indexOf(':', i);
        if (i < 0) return out;

        i++;
        i = skipWs(json, i);
        if (i >= json.length() || json.charAt(i) != '[') return out;

        int end = findArrayEnd(json, i);
        if (end <= i) return out;

        int p = i + 1;
        while (p < end) {
            p = skipWs(json, p);
            if (p >= end) break;

            char c = json.charAt(p);
            if (c == ',') {
                p++;
                continue;
            }
            if (c == ']') break;
            if (c != Q) {
                p++;
                continue;
            }

            int qEnd = findStringEnd(json, p + 1);
            if (qEnd < 0 || qEnd > end) break;

            String val = unescape(json.substring(p + 1, qEnd));
            if (!val.isBlank()) out.add(val.trim());
            p = qEnd + 1;
        }
        return out;
    }

    private static String readStringFrom(String json, String key, int fromIndex) {
        if (json == null || key == null) return "";
        if (fromIndex < 0) fromIndex = 0;
        if (fromIndex >= json.length()) return "";

        int i = json.indexOf(Q + key + Q, fromIndex);
        if (i < 0) return "";

        i = json.indexOf(':', i);
        if (i < 0) return "";

        i++;
        i = skipWs(json, i);
        if (i >= json.length() || json.charAt(i) != Q) return "";

        int j = findStringEnd(json, i + 1);
        if (j < 0) return "";
        return unescape(json.substring(i + 1, j));
    }

    private static long readLongFrom(String json, String key, int fromIndex) {
        if (json == null || key == null) return 0L;
        if (fromIndex < 0) fromIndex = 0;
        if (fromIndex >= json.length()) return 0L;

        int i = json.indexOf(Q + key + Q, fromIndex);
        if (i < 0) return 0L;

        i = json.indexOf(':', i);
        if (i < 0) return 0L;

        i++;
        i = skipWs(json, i);

        int j = i;
        while (j < json.length()) {
            char c = json.charAt(j);
            if ((c >= '0' && c <= '9') || c == '-') {
                j++;
                continue;
            }
            break;
        }

        if (j <= i) return 0L;
        try {
            return Math.max(0L, Long.parseLong(json.substring(i, j).trim()));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private static String readString(String json, String key) {
        return readStringFrom(json, key, 0);
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == NL || c == CR || c == TAB) i++;
            else break;
        }
        return i;
    }

    private static int findStringEnd(String s, int from) {
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == BS) {
                esc = true;
                continue;
            }
            if (c == Q) return i;
        }
        return -1;
    }

    private static String unescape(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != BS) {
                out.append(c);
                continue;
            }
            if (i + 1 >= s.length()) break;
            char n = s.charAt(i + 1);

            if (n == Q) {
                out.append(Q);
                i++;
            } else if (n == BS) {
                out.append(BS);
                i++;
            } else if (n == 'n' || n == 'r' || n == 't') {
                out.append(' ');
                i++;
            } else {
                out.append(n);
                i++;
            }
        }

        return out.toString().trim();
    }
}
