// FILE: src/main/java/app/ui/UrlTitleExtractor.java
package app.ui;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Универсальный "юзерфрендли" извлекатель короткого заголовка из URL.
 *
 * Принципы:
 * - без сетевых запросов
 * - пытаемся вытащить самый "смысловой" короткий идентификатор (issue key, PR/issue номер, версия сборки и т.п.)
 * - если не получилось — даём аккуратный fallback (host / humanized tail)
 */
public final class UrlTitleExtractor {

    private UrlTitleExtractor() {}

    // ISSUE key: ABC-123, ABCD-1, A1B-12 (достаточно широко)
    private static final Pattern ISSUE_KEY = Pattern.compile("(?i)\\b([A-Z][A-Z0-9]{1,14}-\\d{1,9})\\b");

    // Versions like 1.1.0.4546 (your build example)
    private static final Pattern VERSION_4 = Pattern.compile("\\b(\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}\\.\\d{1,6})\\b");

    // GitHub: /owner/repo/issues/123 or /pull/123
    private static final Pattern GITHUB_ISSUE = Pattern.compile("^/([^/]+)/([^/]+)/(issues|pull)/([0-9]+)(/.*)?$");

    // GitLab: /group/repo/-/issues/123 or /merge_requests/123 (group может быть вложенный, но без фанатизма)
    private static final Pattern GITLAB_ITEM = Pattern.compile("^/([^/]+)/([^/]+)(/[^/]+)*/-/(issues|merge_requests)/([0-9]+)(/.*)?$");

    // Bitbucket: /projects/PROJ/repos/repo/pull-requests/123 or /issues/123 (вариации)
    private static final Pattern BITBUCKET_PR = Pattern.compile("^/projects/([^/]+)/repos/([^/]+)/pull-requests/([0-9]+)(/.*)?$");
    private static final Pattern BITBUCKET_ISSUE = Pattern.compile("^/projects/([^/]+)/repos/([^/]+)/issues/([0-9]+)(/.*)?$");

    // Trello: /c/<shortId>/<slug>
    private static final Pattern TRELLO_CARD = Pattern.compile("^/c/([A-Za-z0-9]{6,})/([^/?#]+).*$");

    // Figma: /file/<id>/<name> or /design/<id>/<name>
    private static final Pattern FIGMA_FILE = Pattern.compile("^/(file|design)/([^/]+)/([^/?#]+).*$");

    // Notion: .../<title>-<hex...>
    private static final Pattern NOTION_TITLE = Pattern.compile("^(.*?)(?:-[0-9a-fA-F]{16,})$");

    // Google Docs/Sheets/Slides: /document/d/<id>/... etc (title unknown offline, but type is valuable)
    private static final Pattern GDRIVE_DOC = Pattern.compile("^/(document|spreadsheets|presentation)/d/([^/]+).*$");

    // Jira Cloud: /browse/ABC-123 (issue key already matches, но иногда полезно)
    private static final Pattern JIRA_BROWSE = Pattern.compile("^/browse/([A-Za-z][A-Za-z0-9]{1,14}-\\d{1,9}).*$", Pattern.CASE_INSENSITIVE);

    // YouTrack: /issue/ABC-123
    private static final Pattern YOUTRACK_ISSUE = Pattern.compile("^/issue/([A-Za-z][A-Za-z0-9]{1,14}-\\d{1,9}).*$", Pattern.CASE_INSENSITIVE);

    /**
     * Главный метод: возвращает короткий "заголовок" для отображения.
     */
    public static String extractDisplayTitle(String rawUrl) {
        String u = safe(rawUrl);
        if (u.isBlank()) return "";

        // 0) Version signal (works even if URL is weird / contains it in fragment)
        String ver0 = findVersion4(u);
        if (!ver0.isBlank()) return clampTitle(ver0);

        // Normalization for parsing (URI лучше переносит современные URL, но требует scheme)
        String normalized = u;
        if (!startsWithHttp(normalized)) normalized = "https://" + normalized;

        URI uri = tryParseUri(normalized);
        if (uri == null) {
            String key = findIssueKey(u);
            if (!key.isBlank()) return clampTitle(key.toUpperCase(Locale.ROOT));
            return clampTitle(u);
        }

        String host = safe(uri.getHost()).toLowerCase(Locale.ROOT);
        host = stripWww(host);

        String path = safe(uri.getPath());
        String query = safe(uri.getQuery());
        String fragment = safe(uri.getFragment());

        String all = path + " " + query + " " + fragment;

        // 1) Version (after parsing too, in case it is only in fragment/query)
        String ver = findVersion4(all);
        if (!ver.isBlank()) return clampTitle(ver);

        // 2) Strong signal: issue key anywhere
        String key = findIssueKey(all);
        if (!key.isBlank()) return clampTitle(key.toUpperCase(Locale.ROOT));

        // 3) Host-specific patterns (PR/issue/doc types, etc.)
        String byHost = tryHostSpecific(host, path);
        if (!byHost.isBlank()) return clampTitle(byHost);

        // 4) Generic humanized tail
        String tail = lastMeaningfulSegment(path);
        if (!tail.isBlank()) {
            String human = humanizeSlug(tail);
            if (!human.isBlank()) return clampTitle(human);
        }

        // 5) Fallback
        if (!host.isBlank()) return clampTitle(host);
        return clampTitle(u);
    }

    // ===================== HOST-SPECIFIC =====================

    private static String tryHostSpecific(String host, String path) {
        if (host.isBlank()) return "";

        // Jira
        Matcher jira = JIRA_BROWSE.matcher(path);
        if (jira.matches()) {
            return jira.group(1).toUpperCase(Locale.ROOT);
        }

        // YouTrack
        Matcher yt = YOUTRACK_ISSUE.matcher(path);
        if (yt.matches()) {
            return yt.group(1).toUpperCase(Locale.ROOT);
        }

        // GitHub
        if (host.equals("github.com")) {
            Matcher m = GITHUB_ISSUE.matcher(path);
            if (m.matches()) {
                String owner = m.group(1);
                String repo = m.group(2);
                String kind = m.group(3); // issues|pull
                String num = m.group(4);
                String marker = kind.equals("pull") ? "PR" : "#";
                return owner + "/" + repo + " " + marker + num;
            }
        }

        // GitLab (gitlab.com и self-hosted, если в host есть "gitlab")
        if (host.endsWith("gitlab.com") || host.contains("gitlab")) {
            Matcher m = GITLAB_ITEM.matcher(path);
            if (m.matches()) {
                String group = m.group(1);
                String repo = m.group(2);
                String kind = m.group(4);
                String num = m.group(5);
                String marker = kind.equals("merge_requests") ? "MR" : "#";
                return group + "/" + repo + " " + marker + num;
            }
        }

        // Bitbucket (Server/DC обычно host кастомный, но path узнаваем)
        Matcher bbPr = BITBUCKET_PR.matcher(path);
        if (bbPr.matches()) {
            String proj = bbPr.group(1);
            String repo = bbPr.group(2);
            String num = bbPr.group(3);
            return proj + "/" + repo + " PR" + num;
        }
        Matcher bbIssue = BITBUCKET_ISSUE.matcher(path);
        if (bbIssue.matches()) {
            String proj = bbIssue.group(1);
            String repo = bbIssue.group(2);
            String num = bbIssue.group(3);
            return proj + "/" + repo + " #" + num;
        }

        // Trello
        if (host.endsWith("trello.com")) {
            Matcher m = TRELLO_CARD.matcher(path);
            if (m.matches()) {
                String slug = decode(m.group(2));
                slug = humanizeSlug(slug);
                return slug.isBlank() ? "Trello" : slug;
            }
        }

        // Figma
        if (host.endsWith("figma.com")) {
            Matcher m = FIGMA_FILE.matcher(path);
            if (m.matches()) {
                String name = decode(m.group(3));
                name = humanizeSlug(name);
                return name.isBlank() ? "Figma" : name;
            }
        }

        // Notion
        if (host.endsWith("notion.so")) {
            String tail = lastMeaningfulSegment(path);
            if (!tail.isBlank()) {
                String decoded = decode(tail);
                Matcher m = NOTION_TITLE.matcher(decoded);
                if (m.matches()) {
                    String title = humanizeSlug(m.group(1));
                    if (!title.isBlank()) return title;
                }
                String h = humanizeSlug(decoded);
                if (!h.isBlank()) return h;
            }
        }

        // Google Docs
        if (host.endsWith("docs.google.com")) {
            Matcher m = GDRIVE_DOC.matcher(path);
            if (m.matches()) {
                String kind = m.group(1);
                return switch (kind) {
                    case "document" -> "Google Doc";
                    case "spreadsheets" -> "Google Sheet";
                    case "presentation" -> "Google Slides";
                    default -> "Google";
                };
            }
        }

        return "";
    }

    // ===================== GENERIC HELPERS =====================

    private static String findVersion4(String s) {
        String v = safe(s);
        if (v.isBlank()) return "";
        Matcher m = VERSION_4.matcher(v);
        if (m.find()) return safe(m.group(1));
        return "";
    }

    private static URI tryParseUri(String s) {
        try {
            return URI.create(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean startsWithHttp(String s) {
        String v = safe(s).toLowerCase(Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }

    private static String stripWww(String host) {
        String h = safe(host);
        if (h.startsWith("www.")) return h.substring(4);
        return h;
    }

    private static String findIssueKey(String s) {
        String v = safe(s);
        if (v.isBlank()) return "";
        Matcher m = ISSUE_KEY.matcher(v);
        if (m.find()) return safe(m.group(1));
        return "";
    }

    private static String lastMeaningfulSegment(String path) {
        String p = safe(path);
        if (p.isBlank()) return "";
        String[] parts = p.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String seg = safe(parts[i]);
            if (seg.isBlank()) continue;

            // common noisy tails
            String low = seg.toLowerCase(Locale.ROOT);
            if (low.equals("edit") || low.equals("view") || low.equals("details") || low.equals("home")) continue;

            return seg;
        }
        return "";
    }

    private static String humanizeSlug(String s) {
        String v = safe(s);
        if (v.isBlank()) return "";

        // drop common extensions
        int dot = v.lastIndexOf('.');
        if (dot > 0 && dot >= v.length() - 6) {
            v = v.substring(0, dot);
        }

        v = decode(v);

        // separators -> spaces
        v = v.replace('_', ' ');
        v = v.replace('-', ' ');

        // collapse spaces
        v = v.replaceAll("\\s+", " ").trim();

        // if looks like pure id/hash
        if (v.matches("(?i)[0-9a-f]{8,}")) return v;

        return v;
    }

    private static String decode(String s) {
        String v = safe(s);
        if (v.isBlank()) return "";
        try {
            return URLDecoder.decode(v, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return v;
        }
    }

    private static String clampTitle(String s) {
        String v = safe(s);
        if (v.isBlank()) return "";

        // tidy protocol in fallbacks
        v = v.replaceFirst("(?i)^https?://", "");
        v = v.replaceFirst("(?i)^www\\.", "");

        final int MAX = 48;
        if (v.length() <= MAX) return v;

        int head = 32;
        int tail = 12;
        if (head + tail + 1 >= MAX) {
            return v.substring(0, MAX - 1) + "…";
        }
        return v.substring(0, head) + "…" + v.substring(v.length() - tail);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
