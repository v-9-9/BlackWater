package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebKnowledgeCollector {

    private static final int MAX_RESULTS_PER_SOURCE = 6;
    private static final int MAX_TOTAL_RESULTS = 60;
    private static final int MAX_CONTENT_LENGTH = 16000;

    private static final String USER_AGENT =
            "BlackwaterResearch/1.0 (+https://github.com/v-9-9/BlackWater)";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private final Map<String, RobotsRules> robotsCache =
            new ConcurrentHashMap<>();

    public WebKnowledgeCollector(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public synchronized List<SourceResult> search(
            String query
    ) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String cleanQuery = normalizeQuery(query);

        List<SourceResult> results =
                Collections.synchronizedList(
                        new ArrayList<>()
                );

        /*
         * Core independent web search.
         * No API key required.
         */
        collectGoogle(cleanQuery, results);
        collectBing(cleanQuery, results);
        collectDuckDuckGo(cleanQuery, results);
        collectWikipedia(cleanQuery, results);

        /*
         * Developer / programming sources.
         */
        collectGitHub(cleanQuery, results);
        collectStackOverflow(cleanQuery, results);
        collectNpm(cleanQuery, results);
        collectMaven(cleanQuery, results);
        collectMdn(cleanQuery, results);
        collectArxiv(cleanQuery, results);
        collectHuggingFace(cleanQuery, results);

        /*
         * Community / media / social discovery.
         *
         * These are public-search/discovery endpoints.
         * They are NOT authentication bypasses.
         */
        collectReddit(cleanQuery, results);
        collectYouTube(cleanQuery, results);
        collectTikTok(cleanQuery, results);
        collectInstagram(cleanQuery, results);

        /*
         * Useful official documentation domains.
         */
        collectOfficialDocumentation(
                cleanQuery,
                results
        );

        return rankAndDeduplicate(
                results,
                cleanQuery
        );
    }

    public List<SourceResult> searchUrl(
            String url
    ) {

        if (url == null || url.isBlank()) {
            return List.of();
        }

        String content =
                fetchPublicPage(url);

        if (content.isBlank()) {
            return List.of();
        }

        String title =
                extractTitle(content);

        String text =
                extractReadableText(content);

        if (text.isBlank()) {
            return List.of();
        }

        return List.of(
                new SourceResult(
                        detectSource(url),
                        title.isBlank()
                                ? url
                                : title,
                        limit(
                                text,
                                MAX_CONTENT_LENGTH
                        ),
                        url,
                        1.0
                )
        );
    }

    /*
     * ============================================================
     * SEARCH ENGINES
     * ============================================================
     */

    private void collectGoogle(
            String query,
            List<SourceResult> results
    ) {

        String url =
                "https://www.google.com/search?q="
                        + encode(query)
                        + "&num=10";

        collectSearchHtml(
                "Google",
                url,
                results
        );
    }

    private void collectBing(
            String query,
            List<SourceResult> results
    ) {

        String url =
                "https://www.bing.com/search?q="
                        + encode(query)
                        + "&count=10";

        collectSearchHtml(
                "Bing",
                url,
                results
        );
    }

    private void collectDuckDuckGo(
            String query,
            List<SourceResult> results
    ) {

        String htmlUrl =
                "https://html.duckduckgo.com/html/?q="
                        + encode(query);

        collectSearchHtml(
                "DuckDuckGo",
                htmlUrl,
                results
        );

        /*
         * Also try the public instant-answer endpoint.
         */
        try {

            String response =
                    get(
                            "https://api.duckduckgo.com/?q="
                                    + encode(query)
                                    + "&format=json"
                                    + "&no_html=1"
                                    + "&skip_disambig=1"
                    );

            if (response.isBlank()) {
                return;
            }

            JsonNode root =
                    objectMapper.readTree(response);

            String abstractText =
                    root.path(
                            "AbstractText"
                    ).asText("");

            String abstractUrl =
                    root.path(
                            "AbstractURL"
                    ).asText("");

            String heading =
                    root.path(
                            "Heading"
                    ).asText("");

            if (!abstractText.isBlank()) {

                results.add(
                        new SourceResult(
                                "DuckDuckGo",
                                heading.isBlank()
                                        ? query
                                        : heading,
                                abstractText,
                                abstractUrl,
                                0.70
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void collectSearchHtml(
            String source,
            String url,
            List<SourceResult> results
    ) {

        try {

            String html =
                    get(
                            url
                    );

            if (html.isBlank()) {
                return;
            }

            List<LinkResult> links =
                    extractSearchLinks(
                            html,
                            source
                    );

            for (
                    int i = 0;
                    i < Math.min(
                            links.size(),
                            MAX_RESULTS_PER_SOURCE
                    );
                    i++
            ) {

                LinkResult link =
                        links.get(i);

                String page =
                        fetchPublicPage(
                                link.url()
                        );

                String content =
                        extractReadableText(
                                page
                        );

                if (content.isBlank()) {
                    content =
                            link.snippet();
                }

                results.add(
                        new SourceResult(
                                source,
                                link.title(),
                                limit(
                                        content,
                                        MAX_CONTENT_LENGTH
                                ),
                                link.url(),
                                0.65
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    /*
     * ============================================================
     * KNOWLEDGE SOURCES
     * ============================================================
     */

    private void collectWikipedia(
            String query,
            List<SourceResult> results
    ) {

        try {

            String response =
                    get(
                            "https://en.wikipedia.org/w/api.php"
                                    + "?action=query"
                                    + "&list=search"
                                    + "&srsearch="
                                    + encode(query)
                                    + "&format=json"
                                    + "&utf8=1"
                    );

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode search =
                    root.path("query")
                            .path("search");

            if (!search.isArray()) {
                return;
            }

            int count =
                    Math.min(
                            search.size(),
                            MAX_RESULTS_PER_SOURCE
                    );

            for (
                    int i = 0;
                    i < count;
                    i++
            ) {

                JsonNode item =
                        search.get(i);

                String title =
                        item.path(
                                "title"
                        ).asText("");

                String snippet =
                        cleanHtml(
                                item.path(
                                        "snippet"
                                ).asText("")
                        );

                if (title.isBlank()) {
                    continue;
                }

                String url =
                        "https://en.wikipedia.org/wiki/"
                                + encodePath(
                                        title
                                );

                results.add(
                        new SourceResult(
                                "Wikipedia",
                                title,
                                snippet,
                                url,
                                0.90
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void collectGitHub(
            String query,
            List<SourceResult> results
    ) {

        try {

            String response =
                    getWithHeaders(
                            "https://github.com/search?q="
                                    + encode(query)
                                    + "&type=repositories"
                    );

            List<LinkResult> links =
                    extractGitHubLinks(
                            response
                    );

            for (
                    int i = 0;
                    i < Math.min(
                            links.size(),
                            MAX_RESULTS_PER_SOURCE
                    );
                    i++
            ) {

                LinkResult link =
                        links.get(i);

                results.add(
                        new SourceResult(
                                "GitHub",
                                link.title(),
                                link.snippet(),
                                link.url(),
                                0.95
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void collectStackOverflow(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "Stack Overflow",
                "stackoverflow.com",
                query,
                results,
                0.92
        );
    }

    private void collectNpm(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "npm",
                "npmjs.com",
                query,
                results,
                0.88
        );
    }

    private void collectMaven(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "Maven Central",
                "central.sonatype.com",
                query,
                results,
                0.88
        );
    }

    private void collectMdn(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "MDN",
                "developer.mozilla.org",
                query,
                results,
                0.95
        );
    }

    private void collectArxiv(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "arXiv",
                "arxiv.org",
                query,
                results,
                0.90
        );
    }

    private void collectHuggingFace(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "Hugging Face",
                "huggingface.co",
                query,
                results,
                0.92
        );
    }

    /*
     * ============================================================
     * COMMUNITY / VIDEO / SOCIAL
     * ============================================================
     */

    private void collectReddit(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "Reddit",
                "reddit.com",
                query,
                results,
                0.82
        );
    }

    private void collectYouTube(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "YouTube",
                "youtube.com",
                query,
                results,
                0.80
        );
    }

    private void collectTikTok(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "TikTok",
                "tiktok.com",
                query,
                results,
                0.55
        );
    }

    private void collectInstagram(
            String query,
            List<SourceResult> results
    ) {

        collectSearchDomain(
                "Instagram",
                "instagram.com",
                query,
                results,
                0.55
        );
    }

    /*
     * ============================================================
     * OFFICIAL DOCUMENTATION
     * ============================================================
     */

    private void collectOfficialDocumentation(
            String query,
            List<SourceResult> results
    ) {

        String[] domains = {
                "docs.spring.io",
                "docs.oracle.com",
                "docs.python.org",
                "nodejs.org",
                "react.dev",
                "docs.github.com",
                "learn.microsoft.com",
                "developer.android.com",
                "developer.apple.com",
                "docs.docker.com",
                "kubernetes.io",
                "redis.io",
                "postgresql.org"
        };

        for (String domain : domains) {

            collectSearchDomain(
                    domain,
                    domain,
                    query,
                    results,
                    0.96
            );
        }
    }

    private void collectSearchDomain(
            String source,
            String domain,
            String query,
            List<SourceResult> results,
            double confidence
    ) {

        String searchQuery =
                "site:"
                        + domain
                        + " "
                        + query;

        String url =
                "https://www.google.com/search?q="
                        + encode(searchQuery)
                        + "&num=5";

        try {

            String html =
                    get(url);

            if (html.isBlank()) {
                return;
            }

            List<LinkResult> links =
                    extractSearchLinks(
                            html,
                            source
                    );

            for (
                    int i = 0;
                    i < Math.min(
                            links.size(),
                            3
                    );
                    i++
            ) {

                LinkResult link =
                        links.get(i);

                String page =
                        fetchPublicPage(
                                link.url()
                        );

                String content =
                        extractReadableText(
                                page
                        );

                if (content.isBlank()) {
                    content =
                            link.snippet();
                }

                results.add(
                        new SourceResult(
                                source,
                                link.title(),
                                limit(
                                        content,
                                        MAX_CONTENT_LENGTH
                                ),
                                link.url(),
                                confidence
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    /*
     * ============================================================
     * PUBLIC PAGE FETCHER
     * ============================================================
     */

    private String fetchPublicPage(
            String url
    ) {

        if (!isSafeHttpUrl(url)) {
            return "";
        }

        try {

            if (!isAllowedByRobots(url)) {
                return "";
            }

            return get(
                    url
            );

        } catch (Exception ignored) {
            return "";
        }
    }

    private String get(
            String url
    ) {

        return webClientBuilder
                .build()
                .get()
                .uri(url)
                .header(
                        "User-Agent",
                        USER_AGENT
                )
                .header(
                        "Accept-Language",
                        "en-US,en;q=0.8"
                )
                .retrieve()
                .bodyToMono(
                        String.class
                )
                .timeout(
                        Duration.ofSeconds(12)
                )
                .onErrorResume(
                        error ->
                                Mono.just("")
                )
                .blockOptional()
                .orElse("");
    }

    private String getWithHeaders(
            String url
    ) {

        return webClientBuilder
                .build()
                .get()
                .uri(url)
                .header(
                        "User-Agent",
                        USER_AGENT
                )
                .header(
                        "Accept",
                        "text/html,application/xhtml+xml"
                )
                .retrieve()
                .bodyToMono(
                        String.class
                )
                .timeout(
                        Duration.ofSeconds(12)
                )
                .onErrorResume(
                        error ->
                                Mono.just("")
                )
                .blockOptional()
                .orElse("");
    }

    /*
     * ============================================================
     * ROBOTS.TXT
     * ============================================================
     */

    private boolean isAllowedByRobots(
            String url
    ) {

        try {

            URI uri =
                    URI.create(url);

            String host =
                    uri.getHost();

            if (host == null) {
                return false;
            }

            RobotsRules cached =
                    robotsCache.get(host);

            if (cached != null) {
                return cached.isAllowed(
                        uri.getPath()
                );
            }

            String robotsUrl =
                    uri.getScheme()
                            + "://"
                            + host
                            + "/robots.txt";

            String robots =
                    get(
                            robotsUrl
                    );

            RobotsRules rules =
                    RobotsRules.parse(
                            robots
                    );

            robotsCache.put(
                    host,
                    rules
            );

            return rules.isAllowed(
                    uri.getPath()
            );

        } catch (Exception e) {

            /*
             * If robots.txt cannot be read,
             * do not aggressively crawl.
             */
            return false;
        }
    }

    /*
     * ============================================================
     * HTML EXTRACTION
     * ============================================================
     */

    private List<LinkResult> extractSearchLinks(
            String html,
            String source
    ) {

        List<LinkResult> results =
                new ArrayList<>();

        if (html == null
                || html.isBlank()) {
            return results;
        }

        Pattern pattern =
                Pattern.compile(
                        "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                        Pattern.CASE_INSENSITIVE
                                | Pattern.DOTALL
                );

        Matcher matcher =
                pattern.matcher(
                        html
                );

        Set<String> seen =
                new HashSet<>();

        while (
                matcher.find()
                && results.size() < 20
        ) {

            String url =
                    normalizeSearchUrl(
                            matcher.group(1)
                    );

            String title =
                    cleanHtml(
                            matcher.group(2)
                    );

            if (!isSafeHttpUrl(url)
                    || title.length() < 3
                    || title.length() > 500
                    || !seen.add(url)) {
                continue;
            }

            if (isIgnoredSearchUrl(
                    url,
                    source
            )) {
                continue;
            }

            results.add(
                    new LinkResult(
                            title,
                            "",
                            url
                    )
            );
        }

        return results;
    }

    private List<LinkResult> extractGitHubLinks(
            String html
    ) {

        List<LinkResult> results =
                new ArrayList<>();

        Pattern pattern =
                Pattern.compile(
                        "href=[\"'](/[^\"']+/[^\"']+)[\"']",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher matcher =
                pattern.matcher(
                        html
                );

        Set<String> seen =
                new HashSet<>();

        while (
                matcher.find()
                && results.size() < 10
        ) {

            String path =
                    matcher.group(1);

            if (!path.matches(
                    "/[^/]+/[^/]+"
            )) {
                continue;
            }

            String url =
                    "https://github.com"
                            + path;

            if (!seen.add(url)) {
                continue;
            }

            String name =
                    path.substring(
                            1
                    );

            results.add(
                    new LinkResult(
                            name,
                            "GitHub repository",
                            url
                    )
            );
        }

        return results;
    }

    private String extractReadableText(
            String html
    ) {

        if (html == null
                || html.isBlank()) {
            return "";
        }

        String text =
                html
                        .replaceAll(
                                "(?is)<script[^>]*>.*?</script>",
                                " "
                        )
                        .replaceAll(
                                "(?is)<style[^>]*>.*?</style>",
                                " "
                        )
                        .replaceAll(
                                "(?is)<noscript[^>]*>.*?</noscript>",
                                " "
                        )
                        .replaceAll(
                                "(?is)<svg[^>]*>.*?</svg>",
                                " "
                        )
                        .replaceAll(
                                "(?is)<[^>]+>",
                                " "
                        );

        text =
                cleanHtml(
                        text
                );

        return text
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String extractTitle(
            String html
    ) {

        Matcher matcher =
                Pattern.compile(
                        "(?is)<title[^>]*>(.*?)</title>"
                ).matcher(
                        html
                );

        if (!matcher.find()) {
            return "";
        }

        return cleanHtml(
                matcher.group(1)
        );
    }

    /*
     * ============================================================
     * RANKING
     * ============================================================
     */

    private List<SourceResult> rankAndDeduplicate(
            List<SourceResult> results,
            String query
    ) {

        Map<String, SourceResult> unique =
                new LinkedHashMap<>();

        for (SourceResult result :
                results) {

            if (result == null
                    || result.title().isBlank()
                    || result.content().isBlank()) {
                continue;
            }

            String key =
                    normalizeKey(
                            result.url().isBlank()
                                    ? result.source()
                                            + "|"
                                            + result.title()
                                    : result.url()
                    );

            SourceResult previous =
                    unique.get(key);

            if (previous == null
                    || result.confidence()
                    > previous.confidence()) {

                unique.put(
                        key,
                        result
                );
            }
        }

        List<SourceResult> sorted =
                new ArrayList<>(
                        unique.values()
                );

        sorted.sort(
                Comparator
                        .comparingDouble(
                                (SourceResult result) ->
                                        score(
                                                result,
                                                query
                                        )
                        )
                        .reversed()
        );

        if (sorted.size()
                > MAX_TOTAL_RESULTS) {

            return new ArrayList<>(
                    sorted.subList(
                            0,
                            MAX_TOTAL_RESULTS
                    )
            );
        }

        return sorted;
    }

    private double score(
            SourceResult result,
            String query
    ) {

        double score =
                result.confidence();

        String combined =
                (
                        result.title()
                                + " "
                                + result.content()
                )
                        .toLowerCase(
                                Locale.ROOT
                        );

        String[] terms =
                query
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .split(
                                "\\s+"
                        );

        for (String term : terms) {

            if (term.length() < 3) {
                continue;
            }

            if (combined.contains(term)) {
                score += 0.05;
            }
        }

        return score;
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private String normalizeQuery(
            String query
    ) {

        return query
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String encode(
            String value
    ) {

        return URLEncoder
                .encode(
                        value,
                        StandardCharsets.UTF_8
                );
    }

    private String encodePath(
            String value
    ) {

        return value
                .replace(
                        " ",
                        "_"
                );
    }

    private String cleanHtml(
            String value
    ) {

        return value
                .replaceAll(
                        "<[^>]*>",
                        " "
                )
                .replace(
                        "&amp;",
                        "&"
                )
                .replace(
                        "&quot;",
                        "\""
                )
                .replace(
                        "&#39;",
                        "'"
                )
                .replace(
                        "&lt;",
                        "<"
                )
                .replace(
                        "&gt;",
                        ">"
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String limit(
            String value,
            int max
    ) {

        if (value == null) {
            return "";
        }

        if (value.length() <= max) {
            return value;
        }

        return value.substring(
                0,
                max
        );
    }

    private boolean isSafeHttpUrl(
            String url
    ) {

        try {

            URI uri =
                    URI.create(url);

            return (
                    "https".equalsIgnoreCase(
                            uri.getScheme()
                    )
                    || "http".equalsIgnoreCase(
                            uri.getScheme()
                    )
            )
                    && uri.getHost() != null;

        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeSearchUrl(
            String url
    ) {

        if (url == null) {
            return "";
        }

        String result =
                url.trim();

        if (result.startsWith("//")) {
            result =
                    "https:"
                            + result;
        }

        if (result.startsWith("/")) {
            return "";
        }

        return result;
    }

    private boolean isIgnoredSearchUrl(
            String url,
            String source
    ) {

        String lower =
                url.toLowerCase(
                        Locale.ROOT
                );

        if (lower.startsWith(
                "javascript:"
        )) {
            return true;
        }

        if (lower.contains(
                "/settings"
        )
                || lower.contains(
                        "/preferences"
                )
                || lower.contains(
                        "/accounts"
                )
                || lower.contains(
                        "/login"
                )
                || lower.contains(
                        "/signin"
                )) {
            return true;
        }

        return lower.contains(
                "google.com/search"
        )
                || lower.contains(
                        "bing.com/search"
                )
                || lower.contains(
                        "duckduckgo.com/?"
                );
    }

    private String detectSource(
            String url
    ) {

        String lower =
                url.toLowerCase(
                        Locale.ROOT
                );

        if (lower.contains("github.com")) {
            return "GitHub";
        }

        if (lower.contains("reddit.com")) {
            return "Reddit";
        }

        if (lower.contains("youtube.com")
                || lower.contains("youtu.be")) {
            return "YouTube";
        }

        if (lower.contains("tiktok.com")) {
            return "TikTok";
        }

        if (lower.contains("instagram.com")) {
            return "Instagram";
        }

        if (lower.contains("wikipedia.org")) {
            return "Wikipedia";
        }

        return "Web";
    }

    private String normalizeKey(
            String value
    ) {

        return value
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[?#].*$",
                        ""
                )
                .replaceAll(
                        "/+$",
                        ""
                );
    }

    /*
     * ============================================================
     * DATA TYPES
     * ============================================================
     */

    public record SourceResult(
            String source,
            String title,
            String content,
            String url,
            double confidence
    ) {

        public SourceResult(
                String source,
                String title,
                String content,
                String url
        ) {

            this(
                    source,
                    title,
                    content,
                    url,
                    0.50
            );
        }
    }

    private record LinkResult(
            String title,
            String snippet,
            String url
    ) {
    }

    private static final class RobotsRules {

        private final List<String> disallowed;

        private RobotsRules(
                List<String> disallowed
        ) {

            this.disallowed =
                    disallowed;
        }

        static RobotsRules parse(
                String robots
        ) {

            if (robots == null
                    || robots.isBlank()) {

                return new RobotsRules(
                        List.of()
                );
            }

            List<String> rules =
                    new ArrayList<>();

            boolean applies =
                    false;

            for (
                    String raw :
                    robots.split(
                            "\\R"
                    )
            ) {

                String line =
                        raw
                                .split(
                                        "#",
                                        2
                                )[0]
                                .trim();

                if (line.isBlank()) {
                    continue;
                }

                String lower =
                        line.toLowerCase(
                                Locale.ROOT
                        );

                if (lower.startsWith(
                        "user-agent:"
                )) {

                    String agent =
                            line.substring(
                                    line.indexOf(":")
                                            + 1
                            ).trim();

                    applies =
                            "*".equals(
                                    agent
                            );

                    continue;
                }

                if (applies
                        && lower.startsWith(
                                "disallow:"
                        )) {

                    String path =
                            line.substring(
                                    line.indexOf(":")
                                            + 1
                            ).trim();

                    if (!path.isBlank()) {
                        rules.add(path);
                    }
                }
            }

            return new RobotsRules(
                    rules
            );
        }

        boolean isAllowed(
                String path
        ) {

            if (path == null
                    || path.isBlank()) {
                path = "/";
            }

            for (String blocked :
                    disallowed) {

                if ("/".equals(blocked)
                        || path.startsWith(
                                blocked
                        )) {
                    return false;
                }
            }

            return true;
        }
    }
}
