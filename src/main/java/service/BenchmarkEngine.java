package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BenchmarkEngine {

    private final KnowledgeService knowledgeService;
    private final LearningEngine learningEngine;
    private final AIService aiService;
    private final BenchmarkTaskBank taskBank;
    private final BenchmarkHistory benchmarkHistory;

    public BenchmarkEngine(
            KnowledgeService knowledgeService,
            LearningEngine learningEngine,
            AIService aiService,
            BenchmarkTaskBank taskBank,
            BenchmarkHistory benchmarkHistory
    ) {
        this.knowledgeService = knowledgeService;
        this.learningEngine = learningEngine;
        this.aiService = aiService;
        this.taskBank = taskBank;
        this.benchmarkHistory = benchmarkHistory;
    }

    public synchronized BenchmarkResult run(
            String domain
    ) {

        if (domain == null
                || domain.isBlank()) {

            return new BenchmarkResult(
                    "unknown",
                    0,
                    0,
                    List.of(
                            "No domain specified."
                    )
            );
        }

        String normalized =
                domain.trim()
                        .toLowerCase(Locale.ROOT);

        return switch (normalized) {

            case "knowledge" ->
                    runAITaskBenchmark(
                            "knowledge"
                    );

            case "reasoning" ->
                    runAITaskBenchmark(
                            "reasoning"
                    );

            case "coding" ->
                    runAITaskBenchmark(
                            "coding"
                    );

            case "research" ->
                    runResearchBenchmark();

            case "memory" ->
                    runMemoryBenchmark();

            default ->
                    new BenchmarkResult(
                            normalized,
                            0,
                            0,
                            List.of(
                                    "Unknown benchmark domain."
                            )
                    );
        };
    }

    private BenchmarkResult runAITaskBenchmark(
            String domain
    ) {

        List<BenchmarkTaskBank.BenchmarkTask> tasks =
                taskBank.getTasks(domain);

        if (tasks.isEmpty()) {

            return new BenchmarkResult(
                    domain,
                    0,
                    0,
                    List.of(
                            "No benchmark tasks available."
                    )
            );
        }

        double earnedPoints = 0;
        double maximumPoints = 0;

        int passed = 0;

        List<String> details =
                new ArrayList<>();

        for (
                BenchmarkTaskBank.BenchmarkTask task :
                tasks
        ) {

            int difficulty =
                    Math.max(
                            1,
                            task.difficulty()
                    );

            double points =
                    difficulty;

            maximumPoints += points;

            String response;

            try {

                response =
                        aiService.generate(
                                task.prompt(),
                                "swift",
                                null
                        );

            } catch (Exception e) {

                details.add(
                        "Execution failed: "
                                + task.prompt()
                );

                continue;
            }

            if (matchesExpected(
                    response,
                    task.expected()
            )) {

                earnedPoints += points;
                passed++;

                details.add(
                        "Passed [D"
                                + difficulty
                                + "]: "
                                + task.prompt()
                );

            } else {

                details.add(
                        "Failed [D"
                                + difficulty
                                + "]: "
                                + task.prompt()
                );
            }
        }

        int score =
                maximumPoints <= 0
                        ? 0
                        : (int) Math.round(
                                (
                                        earnedPoints
                                                / maximumPoints
                                ) * 100
                        );

        details.add(
                "Passed: "
                        + passed
                        + "/"
                        + tasks.size()
        );

        details.add(
                "Weighted score: "
                        + score
                        + "/100"
        );

        return new BenchmarkResult(
                domain,
                Math.max(
                        0,
                        Math.min(
                                100,
                                score
                        )
                ),
                tasks.size(),
                details
        );
    }

    private BenchmarkResult runResearchBenchmark() {

        List<BenchmarkTaskBank.BenchmarkTask> tasks =
                taskBank.getTasks(
                        "research"
                );

        if (tasks.isEmpty()) {

            return new BenchmarkResult(
                    "research",
                    0,
                    0,
                    List.of(
                            "No research tasks available."
                    )
            );
        }

        double earnedPoints = 0;
        double maximumPoints = 0;

        int passed = 0;

        List<String> details =
                new ArrayList<>();

        for (
                BenchmarkTaskBank.BenchmarkTask task :
                tasks
        ) {

            int difficulty =
                    Math.max(
                            1,
                            task.difficulty()
                    );

            maximumPoints += difficulty;

            String result;

            try {

                result =
                        learningEngine.learn(
                                task.prompt()
                        );

            } catch (Exception e) {

                details.add(
                        "Research failed: "
                                + task.prompt()
                );

                continue;
            }

            boolean useful =
                    result != null
                            && !result.isBlank()
                            && !result.contains(
                                    "No information"
                            )
                            && !result.contains(
                                    "not useful"
                            );

            if (!useful) {

                details.add(
                        "Research failed [D"
                                + difficulty
                                + "]: "
                                + task.prompt()
                );

                continue;
            }

            boolean knowledgeAvailable;

            try {

                knowledgeAvailable =
                        !knowledgeService
                                .search(
                                        task.expected()
                                                .getFirst()
                                )
                                .isEmpty();

            } catch (Exception e) {

                knowledgeAvailable = false;
            }

            if (knowledgeAvailable) {

                earnedPoints += difficulty;
                passed++;

                details.add(
                        "Research passed [D"
                                + difficulty
                                + "]: "
                                + task.prompt()
                );

            } else {

                details.add(
                        "Research completed but "
                                + "verification failed: "
                                + task.prompt()
                );
            }
        }

        int score =
                maximumPoints <= 0
                        ? 0
                        : (int) Math.round(
                                (
                                        earnedPoints
                                                / maximumPoints
                                ) * 100
                        );

        details.add(
                "Passed: "
                        + passed
                        + "/"
                        + tasks.size()
        );

        return new BenchmarkResult(
                "research",
                Math.max(
                        0,
                        Math.min(
                                100,
                                score
                        )
                ),
                tasks.size(),
                details
        );
    }

    private BenchmarkResult runMemoryBenchmark() {

        List<BenchmarkTaskBank.BenchmarkTask> tasks =
                taskBank.getTasks(
                        "memory"
                );

        if (tasks.isEmpty()) {

            return new BenchmarkResult(
                    "memory",
                    0,
                    0,
                    List.of(
                            "No memory tasks available."
                    )
            );
        }

        int score = 0;

        List<String> details =
                new ArrayList<>();

        long knowledgeCount = 0;

        try {

            knowledgeCount =
                    knowledgeService.count();

        } catch (Exception ignored) {
        }

        if (knowledgeCount > 0) {

            score += 50;

            details.add(
                    "Persistent information exists."
            );

        } else {

            details.add(
                    "No persistent knowledge exists."
            );
        }

        try {

            List<String> recent =
                    knowledgeService.getRecent(5);

            if (!recent.isEmpty()) {

                score += 50;

                details.add(
                        "Stored information can be recalled."
                );

            } else {

                details.add(
                        "Stored information could not be recalled."
                );
            }

        } catch (Exception e) {

            details.add(
                    "Recall test failed."
            );
        }

        return new BenchmarkResult(
                "memory",
                Math.min(
                        100,
                        score
                ),
                tasks.size(),
                details
        );
    }

    private boolean matchesExpected(
            String response,
            List<String> expected
    ) {

        if (response == null
                || response.isBlank()
                || expected == null
                || expected.isEmpty()) {

            return false;
        }

        String normalizedResponse =
                response
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^\\p{L}\\p{N}+.#_-]+",
                                " "
                        )
                        .trim();

        for (String expectedValue :
                expected) {

            if (expectedValue == null
                    || expectedValue.isBlank()) {
                continue;
            }

            String normalizedExpected =
                    expectedValue
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .trim();

            if (normalizedResponse.contains(
                    normalizedExpected
            )) {

                return true;
            }
        }

        return false;
    }

    public synchronized BenchmarkSummary runAll() {

        BenchmarkResult knowledge =
                run(
                        "knowledge"
                );

        BenchmarkResult reasoning =
                run(
                        "reasoning"
                );

        BenchmarkResult research =
                run(
                        "research"
                );

        BenchmarkResult coding =
                run(
                        "coding"
                );

        BenchmarkResult memory =
                run(
                        "memory"
                );

        int total =
                knowledge.score()
                        + reasoning.score()
                        + research.score()
                        + coding.score()
                        + memory.score();

        int average =
                total / 5;

        BenchmarkSummary summary =
                new BenchmarkSummary(
                        average,
                        knowledge,
                        reasoning,
                        research,
                        coding,
                        memory
                );

        try {

            benchmarkHistory.record(
                    summary
            );

        } catch (Exception ignored) {
        }

        return summary;
    }

    public synchronized BenchmarkHistory
    getHistoryService() {

        return benchmarkHistory;
    }

    public record BenchmarkResult(
            String domain,
            int score,
            int testsCompleted,
            List<String> details
    ) {
    }

    public record BenchmarkSummary(
            int averageScore,
            BenchmarkResult knowledge,
            BenchmarkResult reasoning,
            BenchmarkResult research,
            BenchmarkResult coding,
            BenchmarkResult memory
    ) {
    }
}
