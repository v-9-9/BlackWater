package service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ResearchEngine {

    private static final int MAX_QUERIES = 6;
    private static final int MAX_RESULTS = 40;
    private static final int MAX_CONTEXT_LENGTH = 50000;

    private final WebKnowledgeCollector webKnowledgeCollector;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(8);

    public ResearchEngine(
            WebKnowledgeCollector webKnowledgeCollector
    ) {
        this.webKnowledgeCollector =
                webKnowledgeCollector;
    }

    public ResearchResult research(
            String question
    ) {

        if (question == null
                || question.isBlank()) {

            return new ResearchResult(
                    "",
                    List.of(),
                    List.of(),
                    0,
                    false
            );
        }

        String cleanQuestion =
                normalize(question);

        List<String> queries =
                buildQueries(cleanQuestion);

        List<SourceResult> collected =
                new ArrayList<>();

        collected.addAll(
                runParallelSearch(queries)
        );

        if (isWeakResearch(collected)) {

            List<String> secondaryQueries =
                    buildSecondaryQueries(
                            cleanQuestion,
                            collected
                    );

            collected.addAll(
                    runParallelSearch(
                            secondaryQueries
                    )
            );
        }

        List<SourceResult> finalResults =
                rankAndDeduplicate(
                        collected,
                        cleanQuestion
                );

        String context =
                buildContext(finalResults);

        List<String> gaps =
                detectGaps(
                        cleanQuestion,
                        finalResults
                );

        return new ResearchResult(
                cleanQuestion,
                finalResults,
                gaps,
                finalResults.size(),
                !finalResults.isEmpty(),
                context
        );
    }

    private List<String> buildQueries(
            String question
    ) {

        LinkedHashSet<String> queries =
                new LinkedHashSet<>();

        queries.add(question);

        queries.add(
                "\"" + question + "\""
        );

        queries.add(
                question + " explanation facts"
        );

        queries.add(
                question + " evidence sources"
        );

        if (looksTechnical(question)) {

            queries.add(
                    question + " documentation"
            );

            queries.add(
                    question + " github implementation"
            );
        }

        return limitQueries(queries);
    }

    private List<String> buildSecondaryQueries(
            String question,
            List<SourceResult> existing
    ) {

        LinkedHashSet<String> queries =
                new LinkedHashSet<>();

        queries.add(
                question + " latest information"
        );

        queries.add(
                question + " official documentation"
        );

        queries.add(
                question + " independent sources"
        );

        if (looksTechnical(question)) {

            queries.add(
                    question + " stackoverflow"
            );

            queries.add(
                    question + " github"
            );

            queries.add(
                    question + " examples"
            );
        }

        existing.stream()
                .limit(5)
                .map(SourceResult::title)
                .filter(
                        title ->
                                title != null
                                        && !title.isBlank()
                )
                .forEach(
                        title ->
                                queries.add(
                                        question
                                                + " "
                                                + title
                                )
                );

        return limitQueries(queries);
    }

    private List<String> limitQueries(
            Collection<String> queries
    ) {

        return queries.stream()
                .map(this::normalize)
                .filter(
                        query ->
                                !query.isBlank()
                )
                .limit(MAX_QUERIES)
                .toList();
    }

    private List<SourceResult> runParallelSearch(
            List<String> queries
    ) {

        if (queries == null
                || queries.isEmpty()) {

            return List.of();
        }

        List<CompletableFuture<List<SourceResult>>>
                futures =
                queries.stream()
                        .map(
                                query ->
                                        CompletableFuture
                                                .supplyAsync(
                                                        () ->
                                                                safeSearch(query),
                                                        executor
                                                )
                        )
                        .toList();

        List<SourceResult> results =
                new ArrayList<>();

        for (
                CompletableFuture<List<SourceResult>>
                        future :
                        futures
        ) {

            try {

                results.addAll(
                        future.get(
                                20,
                                TimeUnit.SECONDS
                        )
                );

            } catch (InterruptedException e) {

                Thread.currentThread()
                        .interrupt();

            } catch (
                    ExecutionException
                    | TimeoutException ignored
            ) {
            }
        }

        return results;
    }

    private List<SourceResult> safeSearch(
            String query
    ) {

        try {

            List<WebKnowledgeCollector.SourceResult>
                    rawResults =
                    webKnowledgeCollector.search(
                            query
                    );

            if (rawResults == null
                    || rawResults.isEmpty()) {
                return List.of();
            }

            return rawResults.stream()
                    .filter(Objects::nonNull)
                    .map(
                            result ->
                                    new SourceResult(
                                            result.source(),
                                            result.title(),
                                            result.content(),
                                            result.url(),
                                            result.confidence()
                                    )
                    )
                    .toList();

        } catch (Exception ignored) {

            return List.of();
        }
    }

    private List<SourceResult> rankAndDeduplicate(
            List<SourceResult> results,
            String question
    ) {

        Map<String, SourceResult> unique =
                new LinkedHashMap<>();

        for (SourceResult result : results) {

            if (result == null) {
                continue;
            }

            if (result.title() == null
                    || result.title().isBlank()) {
                continue;
            }

            if (result.content() == null
                    || result.content().isBlank()) {
                continue;
            }

            String key =
                    normalizeKey(result.url());

            SourceResult previous =
                    unique.get(key);

            if (previous == null
                    || quality(result, question)
                    > quality(previous, question)) {

                unique.put(key, result);
            }
        }

        List<SourceResult> sorted =
                new ArrayList<>(unique.values());

        sorted.sort(
                Comparator
                        .comparingDouble(
                                result ->
                                        quality(
                                                result,
                                                question
                                        )
                        )
                        .reversed()
        );

        if (sorted.size() > MAX_RESULTS) {

            return new ArrayList<>(
                    sorted.subList(
                            0,
                            MAX_RESULTS
                    )
            );
        }

        return sorted;
    }

    private double quality(
            SourceResult result,
            String question
    ) {

        double score =
                result.confidence();

        String source =
                safeLower(result.source());

        String title =
                safeLower(result.title());

        String content =
                safeLower(result.content());

        String q =
                safeLower(question);

        if (
                source.contains("official")
                || source.contains("mdn")
                || source.contains("github")
                || source.contains("stackoverflow")
                || source.contains("wikipedia")
                || source.contains("arxiv")
        ) {
            score += 0.12;
        }

        for (
                String token :
                meaningfulTokens(q)
        ) {

            if (title.contains(token)) {
                score += 0.07;
            }

            if (content.contains(token)) {
                score += 0.025;
            }
        }

        if (content.length() < 100) {
            score -= 0.10;
        }

        if (content.length() > 1000) {
            score += 0.04;
        }

        return score;
    }

    private boolean isWeakResearch(
            List<SourceResult> results
    ) {

        if (results == null
                || results.size() < 5) {
            return true;
        }

        long sources =
                results.stream()
                        .map(SourceResult::source)
                        .filter(Objects::nonNull)
                        .map(
                                value ->
                                        value.toLowerCase(
                                                Locale.ROOT
                                        )
                        )
                        .distinct()
                        .count();

        return sources < 2;
    }

    private List<String> detectGaps(
            String question,
            List<SourceResult> results
    ) {

        List<String> gaps =
                new ArrayList<>();

        if (results.isEmpty()) {

            gaps.add(
                    "No usable web sources were found."
            );

            return gaps;
        }

        if (results.size() < 5) {

            gaps.add(
                    "Research has fewer than five usable sources."
            );
        }

        long sourceCount =
                results.stream()
                        .map(SourceResult::source)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();

        if (sourceCount < 3) {

            gaps.add(
                    "Research has limited source diversity."
            );
        }

        if (looksTechnical(question)) {

            boolean hasCodeSource =
                    results.stream()
                            .anyMatch(
                                    result ->
                                            containsAny(
                                                    safeLower(
                                                            result.source()
                                                    ),
                                                    "github",
                                                    "stackoverflow",
                                                    "mdn",
                                                    "npm",
                                                    "maven"
                                            )
                            );

            if (!hasCodeSource) {

                gaps.add(
                        "No strong programming source was found."
                );
            }
        }

        return gaps;
    }

    private String buildContext(
            List<SourceResult> results
    ) {

        if (results == null
                || results.isEmpty()) {

            return "";
        }

        StringBuilder context =
                new StringBuilder();

        for (
                int i = 0;
                i < results.size();
                i++
        ) {

            SourceResult result =
                    results.get(i);

            context.append("\nSOURCE ");
            context.append(i + 1);

            context.append("\nProvider: ");
            context.append(
                    safe(result.source())
            );

            context.append("\nTitle: ");
            context.append(
                    safe(result.title())
            );

            context.append("\nURL: ");
            context.append(
                    safe(result.url())
            );

            context.append("\nConfidence: ");
            context.append(
                    String.format(
                            Locale.US,
                            "%.2f",
                            result.confidence()
                    )
            );

            context.append("\nContent:\n");
            context.append(
                    safe(result.content())
            );

            context.append("\n\n");

            if (
                    context.length()
                            >= MAX_CONTEXT_LENGTH
            ) {
                break;
            }
        }

        return context.substring(
                0,
                Math.min(
                        context.length(),
                        MAX_CONTEXT_LENGTH
                )
        );
    }

    private boolean looksTechnical(
            String text
    ) {

        String value =
                safeLower(text);

        String[] keywords = {
                "code",
                "coding",
                "program",
                "programming",
                "java",
                "javascript",
                "typescript",
                "python",
                "lua",
                "roblox",
                "spring",
                "api",
                "sdk",
                "github",
                "bug",
                "error",
                "exception",
                "database",
                "server",
                "backend",
                "frontend",
                "html",
                "css",
                "json",
                "docker",
                "kubernetes",
                "linux",
                "android",
                "algorithm",
                "function",
                "class",
                "compiler",
                "software"
        };

        return containsAny(
                value,
                keywords
        );
    }

    private boolean containsAny(
            String value,
            String... terms
    ) {

        for (String term : terms) {

            if (value.contains(term)) {
                return true;
            }
        }

        return false;
    }

    private List<String> meaningfulTokens(
            String text
    ) {

        return Pattern
                .compile(
                        "[\\p{L}\\p{N}]{3,}"
                )
                .matcher(text)
                .results()
                .map(
                        match ->
                                match.group()
                )
                .filter(
                        token ->
                                !STOP_WORDS.contains(
                                        token
                                )
                )
                .distinct()
                .limit(20)
                .collect(
                        Collectors.toList()
                );
    }

    private String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String normalizeKey(
            String url
    ) {

        if (url == null
                || url.isBlank()) {

            return UUID.randomUUID()
                    .toString();
        }

        return url
                .toLowerCase(Locale.ROOT)
                .replaceAll(
                        "[?#].*$",
                        ""
                )
                .replaceAll(
                        "/+$",
                        ""
                );
    }

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }

    private String safeLower(
            String value
    ) {

        return safe(value)
                .toLowerCase(Locale.ROOT);
    }

    private static final Set<String>
            STOP_WORDS =
            Set.of(
                    "the",
                    "and",
                    "for",
                    "with",
                    "that",
                    "this",
                    "from",
                    "what",
                    "when",
                    "where",
                    "which",
                    "about",
                    "how",
                    "why",
                    "are",
                    "was",
                    "were",
                    "you",
                    "your",
                    "can",
                    "does",
                    "have",
                    "has",
                    "into",
                    "using",
                    "use",
                    "على",
                    "الى",
                    "إلى",
                    "من",
                    "في",
                    "عن",
                    "ما",
                    "ماذا",
                    "كيف",
                    "ليش",
                    "شنو",
                    "هذا",
                    "هذه"
            );

    public record ResearchResult(
            String question,
            List<SourceResult> sources,
            List<String> gaps,
            int sourceCount,
            boolean successful,
            String context
    ) {

        public ResearchResult(
                String question,
                List<SourceResult> sources,
                List<String> gaps,
                int sourceCount,
                boolean successful
        ) {

            this(
                    question,
                    sources,
                    gaps,
                    sourceCount,
                    successful,
                    ""
            );
        }
    }

    public record SourceResult(
            String source,
            String title,
            String content,
            String url,
            double confidence
    ) {
    }
}
