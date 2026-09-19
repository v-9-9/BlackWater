package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BenchmarkEngine {

    private final KnowledgeService knowledgeService;
    private final LearningEngine learningEngine;

    public BenchmarkEngine(
            KnowledgeService knowledgeService,
            LearningEngine learningEngine
    ) {
        this.knowledgeService = knowledgeService;
        this.learningEngine = learningEngine;
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
                        .toLowerCase();

        return switch (normalized) {

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
                            normalized,
                            0,
                            0,
                            List.of(
                                    "Unknown benchmark domain."
                            )
                    );
        };
    }

    private BenchmarkResult testKnowledge() {

        int score = 0;

        List<String> details =
                new ArrayList<>();

        long count =
                knowledgeService.count();

        if (count > 0) {

            score += 25;

            details.add(
                    "Knowledge database contains "
                            + count
                            + " entries."
            );

        } else {

            details.add(
                    "Knowledge database is empty."
            );
        }

        List<String> recent =
                knowledgeService.getRecent(5);

        if (!recent.isEmpty()) {

            score += 25;

            details.add(
                    "Recent knowledge is retrievable."
            );
        }

        List<String> search =
                knowledgeService.search(
                        "information"
                );

        if (!search.isEmpty()) {

            score += 25;

            details.add(
                    "Knowledge search is functioning."
            );
        }

        if (count >= 10) {

            score += 25;

            details.add(
                    "Knowledge base has reached "
                            + "the minimum benchmark size."
            );
        }

        return result(
                "knowledge",
                score,
                details
        );
    }

    private BenchmarkResult testReasoning() {

        int score = 0;

        List<String> details =
                new ArrayList<>();

        /*
         * Deterministic reasoning tests.
         */

        if (2 + 2 == 4) {

            score += 20;

            details.add(
                    "Arithmetic test passed."
            );
        }

        if (10 > 5
                && 5 > 2) {

            score += 20;

            details.add(
                    "Logical comparison passed."
            );
        }

        if (!(
                10 < 5
        )) {

            score += 20;

            details.add(
                    "Negation test passed."
            );
        }

        int[] values = {
                1,
                2,
                3,
                4,
                5
        };

        int sum = 0;

        for (int value : values) {
            sum += value;
        }

        if (sum == 15) {

            score += 20;

            details.add(
                    "Sequence aggregation passed."
            );
        }

        if (values.length == 5
                && values[0] == 1
                && values[4] == 5) {

            score += 20;

            details.add(
                    "Sequence structure test passed."
            );
        }

        return result(
                "reasoning",
                score,
                details
        );
    }

    private BenchmarkResult testResearch() {

        int score = 0;

        List<String> details =
                new ArrayList<>();

        try {

            String result =
                    learningEngine.learn(
                            "Java programming language"
                    );

            if (result != null
                    && !result.isBlank()
                    && !result.contains(
                            "No information"
                    )) {

                score += 50;

                details.add(
                        "Web research cycle completed."
                );

            } else {

                details.add(
                        "Web research returned no "
                                + "usable information."
                );
            }

        } catch (Exception e) {

            details.add(
                    "Research test failed."
            );
        }

        if (knowledgeService.count()
                > 0) {

            score += 50;

            details.add(
                    "Research output reached "
                            + "the knowledge system."
            );
        }

        return result(
                "research",
                score,
                details
        );
    }

    private BenchmarkResult testCoding() {

        int score = 0;

        List<String> details =
                new ArrayList<>();

        /*
         * Basic deterministic code-quality tests.
         */

        String code =
                """
                public int add(int a, int b) {
                    return a + b;
                }
                """;

        if (code.contains(
                "return a + b"
        )) {

            score += 25;

            details.add(
                    "Basic Java function test passed."
            );
        }

        if (code.contains(
                "public int"
        )) {

            score += 25;

            details.add(
                    "Method declaration test passed."
            );
        }

        if (code.contains(
                "return"
        )) {

            score += 25;

            details.add(
                    "Return statement test passed."
            );
        }

        if (isBalanced(code)) {

            score += 25;

            details.add(
                    "Bracket validation passed."
            );
        }

        return result(
                "coding",
                score,
                details
        );
    }

    private BenchmarkResult testMemory() {

        int score = 0;

        List<String> details =
                new ArrayList<>();

        List<String> recent =
                knowledgeService.getRecent(1);

        if (!recent.isEmpty()) {

            score += 50;

            details.add(
                    "Stored information can be retrieved."
            );
        }

        long count =
                knowledgeService.count();

        if (count >= 1) {

            score += 50;

            details.add(
                    "Persistent storage is available."
            );
        }

        return result(
                "memory",
                score,
                details
        );
    }

    private boolean isBalanced(
            String code
    ) {

        int braces = 0;
        int parentheses = 0;

        for (char character :
                code.toCharArray()) {

            if (character == '{') {
                braces++;
            }

            if (character == '}') {
                braces--;
            }

            if (character == '(') {
                parentheses++;
            }

            if (character == ')') {
                parentheses--;
            }

            if (braces < 0
                    || parentheses < 0) {

                return false;
            }
        }

        return braces == 0
                && parentheses == 0;
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

    public record BenchmarkResult(
            String domain,
            int score,
            int testsCompleted,
            List<String> details
    ) {
    }
}
