package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BenchmarkEngine {

    private final BenchmarkHistory history;
    private final LearningEngine learningEngine;
    private final AIService aiService;
    private final KnowledgeService knowledgeService;
    private final ImprovementSandbox sandbox;

    public BenchmarkEngine(
            BenchmarkHistory history,
            LearningEngine learningEngine,
            AIService aiService,
            KnowledgeService knowledgeService,
            ImprovementSandbox sandbox
    ) {
        this.history = history;
        this.learningEngine = learningEngine;
        this.aiService = aiService;
        this.knowledgeService = knowledgeService;
        this.sandbox = sandbox;
    }

    public BenchmarkResult run(String domain) {
        return run(domain, null);
    }

    public BenchmarkResult run(
            String domain,
            String experimentId
    ) {
        String normalized = normalizeDomain(domain);

        List<BenchmarkTaskBank.BenchmarkTask> tasks =
                BenchmarkTaskBank.getTasks(normalized);

        if (tasks.isEmpty()) {
            return new BenchmarkResult(
                    normalized,
                    0.0,
                    0,
                    0,
                    experimentId
            );
        }

        List<Double> scores = new ArrayList<>();

        for (BenchmarkTaskBank.BenchmarkTask task : tasks) {
            try {
                double score =
                        evaluateTask(
                                normalized,
                                task,
                                experimentId
                        );

                scores.add(clamp(score));
            } catch (Exception e) {
                scores.add(0.0);
            }
        }

        double average =
                scores.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

        int passed = 0;

        for (double score : scores) {
            if (score >= 0.70) {
                passed++;
            }
        }

        return new BenchmarkResult(
                normalized,
                average,
                passed,
                tasks.size(),
                experimentId
        );
    }

    public BenchmarkSummary runAll() {
        Map<String, Double> domainScores =
                new LinkedHashMap<>();

        for (String domain : supportedDomains()) {
            BenchmarkResult result = run(domain);
            domainScores.put(domain, result.averageScore());
        }

        double average =
                domainScores.values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

        history.record(
                average,
                domainScores
        );

        return new BenchmarkSummary(
                average,
                domainScores
        );
    }

    public BenchmarkHistory getHistoryService() {
        return history;
    }

    private double evaluateTask(
            String domain,
            BenchmarkTaskBank.BenchmarkTask task,
            String experimentId
    ) {
        String prompt = buildBenchmarkPrompt(
                domain,
                task,
                experimentId
        );

        String answer;

        switch (domain) {
            case "RESEARCH" -> {
                try {
                    LearningEngine.ResearchResult result =
                            learningEngine.research(task.prompt());

                    if (result == null) {
                        return 0.0;
                    }

                    String researchText =
                            buildResearchEvaluationText(result);

                    return evaluateExpected(
                            researchText,
                            task.expected()
                    );
                } catch (Exception e) {
                    return 0.0;
                }
            }

            case "MEMORY" -> {
                return evaluateMemoryTask(task);
            }

            case "KNOWLEDGE" -> {
                return evaluateKnowledgeTask(task);
            }

            default -> {
                answer = aiService.generate(
                        prompt,
                        "prime",
                        null
                );
            }
        }

        return evaluateExpected(
                answer,
                task.expected()
        );
    }

    private String buildBenchmarkPrompt(
            String domain,
            BenchmarkTaskBank.BenchmarkTask task,
            String experimentId
    ) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                You are being evaluated by Blackwater's internal benchmark.

                Answer the task accurately and directly.

                Do not claim that you performed actions you did not perform.
                Do not invent sources, test results, files, or facts.
                Prefer correctness over verbosity.

                DOMAIN:
                """);

        prompt.append(domain);

        if (experimentId != null && !experimentId.isBlank()) {
            prompt.append("""

                    
                    SANDBOX EXPERIMENT:
                    """);
            prompt.append(experimentId);

            prompt.append("""
                    
                    
                    The experiment may contain a proposed code improvement.
                    Evaluate the task while considering the implementation represented
                    by the experiment when relevant.
                    """);
        }

        prompt.append("\n\nTASK:\n");
        prompt.append(task.prompt());

        return prompt.toString();
    }

    private double evaluateExpected(
            String answer,
            List<String> expected
    ) {
        if (answer == null || answer.isBlank()) {
            return 0.0;
        }

        if (expected == null || expected.isEmpty()) {
            return 0.5;
        }

        String normalizedAnswer =
                normalize(answer);

        int matched = 0;

        for (String expectedValue : expected) {
            if (expectedValue == null
                    || expectedValue.isBlank()) {
                continue;
            }

            String normalizedExpected =
                    normalize(expectedValue);

            if (normalizedExpected.isBlank()) {
                continue;
            }

            if (normalizedAnswer.contains(normalizedExpected)) {
                matched++;
            }
        }

        if (matched == 0) {
            return 0.0;
        }

        return Math.min(
                1.0,
                (double) matched / expected.size()
        );
    }

    private double evaluateKnowledgeTask(
            BenchmarkTaskBank.BenchmarkTask task
    ) {
        if (knowledgeService.count() <= 0) {
            return 0.0;
        }

        String query = task.prompt();

        List<KnowledgeEntry> results =
                knowledgeService.search(query);

        if (results == null || results.isEmpty()) {
            return 0.0;
        }

        double averageConfidence =
                results.stream()
                        .limit(5)
                        .mapToDouble(KnowledgeEntry::confidence)
                        .average()
                        .orElse(0.0);

        double relevance =
                Math.min(
                        1.0,
                        results.size() / 5.0
                );

        return clamp(
                (averageConfidence * 0.60)
                        + (relevance * 0.40)
        );
    }

    private double evaluateMemoryTask(
            BenchmarkTaskBank.BenchmarkTask task
    ) {
        if (knowledgeService.count() <= 0) {
            return 0.0;
        }

        List<KnowledgeEntry> recent =
                knowledgeService.recent(10);

        if (recent == null || recent.isEmpty()) {
            return 0.0;
        }

        String query =
                normalize(task.prompt());

        int relevant = 0;

        for (KnowledgeEntry entry : recent) {
            if (entry == null) {
                continue;
            }

            String searchable =
                    normalize(entry.searchableText());

            if (containsUsefulTerms(query, searchable)) {
                relevant++;
            }
        }

        if (relevant == 0) {
            return 0.0;
        }

        return Math.min(
                1.0,
                0.40 + (relevant * 0.15)
        );
    }

    private boolean containsUsefulTerms(
            String query,
            String searchable
    ) {
        if (query.isBlank() || searchable.isBlank()) {
            return false;
        }

        String[] words =
                query.split("\\s+");

        int useful = 0;

        for (String word : words) {
            if (word.length() < 3) {
                continue;
            }

            if (searchable.contains(word)) {
                useful++;
            }
        }

        return useful >= Math.max(
                1,
                Math.min(3, words.length / 4)
        );
    }

    private String buildResearchEvaluationText(
            LearningEngine.ResearchResult result
    ) {
        StringBuilder text = new StringBuilder();

        if (result.sources() != null) {
            for (ResearchEngine.SourceResult source :
                    result.sources()) {

                if (source == null) {
                    continue;
                }

                text.append(source.title())
                        .append(" ")
                        .append(source.content())
                        .append(" ")
                        .append(source.source())
                        .append(" ")
                        .append(source.url())
                        .append("\n");
            }
        }

        if (result.gaps() != null) {
            text.append("\nGAPS:\n");

            for (String gap : result.gaps()) {
                text.append(gap).append("\n");
            }
        }

        return text.toString();
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "CODING";
        }

        String normalized =
                domain.trim()
                        .toUpperCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_');

        return switch (normalized) {
            case "KNOWLEDGE" -> "KNOWLEDGE";
            case "REASONING" -> "REASONING";
            case "RESEARCH" -> "RESEARCH";
            case "CODING", "CODE", "PROGRAMMING" -> "CODING";
            case "MEMORY" -> "MEMORY";
            default -> "CODING";
        };
    }

    private double clamp(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private List<String> supportedDomains() {
        return List.of(
                "KNOWLEDGE",
                "REASONING",
                "RESEARCH",
                "CODING",
                "MEMORY"
        );
    }

    public record BenchmarkResult(
            String domain,
            double averageScore,
            int passedTasks,
            int totalTasks,
            String experimentId
    ) {
    }

    public record BenchmarkSummary(
            double averageScore,
            Map<String, Double> domainScores
    ) {
    }
}
