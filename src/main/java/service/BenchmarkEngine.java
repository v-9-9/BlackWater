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

    public BenchmarkEngine(
            KnowledgeService knowledgeService,
            LearningEngine learningEngine,
            AIService aiService
    ) {
        this.knowledgeService = knowledgeService;
        this.learningEngine = learningEngine;
        this.aiService = aiService;
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

        return switch (
                domain.trim().toLowerCase(Locale.ROOT)
        ) {

            case "knowledge" ->
                    testKnowledge();

            case "reasoning" ->
                    testReasoning();

            case "research" ->
                    testResearch();

            case "coding" ->
                    testCoding();

            case "memory" ->
                    testMemory();

            default ->
                    new BenchmarkResult(
                            domain,
                            0,
                            0,
                            List.of(
                                    "Unknown benchmark domain."
                            )
                    );
        };
    }

    private BenchmarkResult testKnowledge() {

        List<TestCase> tests =
                List.of(
                        new TestCase(
                                "What is the capital of France?",
                                List.of(
                                        "paris"
                                )
                        ),
                        new TestCase(
                                "What planet is known as the Red Planet?",
                                List.of(
                                        "mars"
                                )
                        ),
                        new TestCase(
                                "What is water made of?",
                                List.of(
                                        "hydrogen",
                                        "oxygen",
                                        "h2o"
                                )
                        )
                );

        return runAITests(
                "knowledge",
                tests
        );
    }

    private BenchmarkResult testReasoning() {

        List<TestCase> tests =
                List.of(
                        new TestCase(
                                "If all cats are animals and Luna is a cat, "
                                        + "is Luna an animal?",
                                List.of(
                                        "yes"
                                )
                        ),
                        new TestCase(
                                "What comes next: 2, 4, 6, 8?",
                                List.of(
                                        "10"
                                )
                        ),
                        new TestCase(
                                "If A is greater than B and B is greater "
                                        + "than C, is A greater than C?",
                                List.of(
                                        "yes"
                                )
                        )
                );

        return runAITests(
                "reasoning",
                tests
        );
    }

    private BenchmarkResult testResearch() {

        int score = 0;

        List<String> details =
                new ArrayList<>();

        try {

            String result =
                    learningEngine.learn(
                            "Java programming language "
                                    + "official documentation"
                    );

            if (result != null
                    && !result.isBlank()
                    && !result.contains(
                            "No information"
                    )
                    && !result.contains(
                            "not useful"
                    )) {

                score += 50;

                details.add(
                        "Research and learning cycle completed."
                );

            } else {

                details.add(
                        "Research returned insufficient information."
                );
            }

        } catch (Exception e) {

            details.add(
                    "Research test failed."
            );
        }

        try {

            List<String> knowledge =
                    knowledgeService.search(
                            "Java programming"
                    );

            if (!knowledge.isEmpty()) {

                score += 50;

                details.add(
                        "Research result is retrievable "
                                + "from the knowledge system."
                );

            } else {

                details.add(
                        "No matching research result "
                                + "was found in knowledge."
                );
            }

        } catch (Exception e) {

            details.add(
                    "Knowledge retrieval test failed."
            );
        }

        return result(
                "research",
                score,
                details
        );
    }

    private BenchmarkResult testCoding() {

        List<TestCase> tests =
                List.of(
                        new TestCase(
                                """
                                Write a Java method named add that
                                receives two integers and returns
                                their sum.
                                """,
                                List.of(
                                        "int",
                                        "add",
                                        "return",
                                        "+"
                                )
                        ),
                        new TestCase(
                                """
                                In Java, which keyword is used to
                                create a subclass from another class?
                                """,
                                List.of(
                                        "extends"
                                )
                        ),
                        new TestCase(
                                """
                                What data structure follows FIFO order?
                                """,
                                List.of(
                                        "queue"
                                )
                        )
                );

        return runAITests(
                "coding",
                tests
        );
    }

    private BenchmarkResult testMemory() {

        List<String> details =
                new ArrayList<>();

        int score = 0;

        try {

            long count =
                    knowledgeService.count();

            if (count > 0) {

                score += 50;

                details.add(
                        "Persistent information exists."
                );
            }

        } catch (Exception e) {

            details.add(
                    "Persistent storage test failed."
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
            }

        } catch (Exception e) {

            details.add(
                    "Recall test failed."
            );
        }

        return result(
                "memory",
                score,
                details
        );
    }

    private BenchmarkResult runAITests(
            String domain,
            List<TestCase> tests
    ) {

        if (tests.isEmpty()) {

            return result(
                    domain,
                    0,
                    List.of(
                            "No tests available."
                    )
            );
        }

        int passed = 0;

        List<String> details =
                new ArrayList<>();

        for (TestCase test :
                tests) {

            String response;

            try {

                response =
                        aiService.generate(
                                test.question(),
                                "swift",
                                null
                        );

            } catch (Exception e) {

                details.add(
                        "Test failed to execute."
                );

                continue;
            }

            if (matchesExpected(
                    response,
                    test.expected()
            )) {

                passed++;

                details.add(
                        "Passed: "
                                + test.question()
                );

            } else {

                details.add(
                        "Failed: "
                                + test.question()
                );
            }
        }

        int score =
                (passed * 100)
                        / tests.size();

        return result(
                domain,
                score,
                details
        );
    }

    private boolean matchesExpected(
            String response,
            List<String> expected
    ) {

        if (response == null
                || response.isBlank()) {

            return false;
        }

        String normalized =
                response
                        .toLowerCase(Locale.ROOT)
                        .replaceAll(
                                "[^\\p{L}\\p{N}+.#_-]+",
                                " "
                        );

        for (String expectedValue :
                expected) {

            if (normalized.contains(
                    expectedValue
                            .toLowerCase(
                                    Locale.ROOT
                            )
            )) {

                return true;
            }
        }

        return false;
    }

    private BenchmarkResult result(
            String domain,
            int score,
            List<String> details
    ) {

        int normalized =
                Math.max(
                        0,
                        Math.min(
                                100,
                                score
                        )
                );

        return new BenchmarkResult(
                domain,
                normalized,
                details.size(),
                details
        );
    }

    private record TestCase(
            String question,
            List<String> expected
    ) {
    }

    public record BenchmarkResult(
            String domain,
            int score,
            int testsCompleted,
            List<String> details
    ) {
    }
}
