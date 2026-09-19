package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ImprovementEngine {

    private final BenchmarkEngine benchmarkEngine;
    private final LearningEngine learningEngine;

    public ImprovementEngine(
            BenchmarkEngine benchmarkEngine,
            LearningEngine learningEngine
    ) {
        this.benchmarkEngine = benchmarkEngine;
        this.learningEngine = learningEngine;
    }

    public synchronized ImprovementResult improveWeakestDomain() {

        List<DomainScore> domains =
                collectBenchmarks();

        if (domains.isEmpty()) {

            return new ImprovementResult(
                    "unknown",
                    0,
                    0,
                    false,
                    "No benchmark data available."
            );
        }

        DomainScore weakest =
                domains.stream()
                        .min(
                                Comparator
                                        .comparingInt(
                                                DomainScore::score
                                        )
                        )
                        .orElse(null);

        if (weakest == null) {

            return new ImprovementResult(
                    "unknown",
                    0,
                    0,
                    false,
                    "Could not identify a weak domain."
            );
        }

        String topic =
                getImprovementTopic(
                        weakest.domain()
                );

        String learningResult;

        try {

            learningResult =
                    learningEngine.learn(
                            topic
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    weakest.domain(),
                    weakest.score(),
                    weakest.score(),
                    false,
                    "Learning failed: "
                            + e.getMessage()
            );
        }

        BenchmarkEngine.BenchmarkResult after =
                benchmarkEngine.run(
                        weakest.domain()
                );

        int before =
                weakest.score();

        int afterScore =
                after.score();

        boolean improved =
                afterScore > before;

        String message;

        if (improved) {

            message =
                    "Domain improved from "
                            + before
                            + "/100 to "
                            + afterScore
                            + "/100.";

        } else {

            message =
                    "No measurable improvement. "
                            + "Benchmark remained at "
                            + afterScore
                            + "/100.";
        }

        return new ImprovementResult(
                weakest.domain(),
                before,
                afterScore,
                improved,
                message
                        + System.lineSeparator()
                        + learningResult
        );
    }

    public synchronized List<DomainScore> collectBenchmarks() {

        List<DomainScore> result =
                new ArrayList<>();

        add(
                result,
                "knowledge"
        );

        add(
                result,
                "reasoning"
        );

        add(
                result,
                "research"
        );

        add(
                result,
                "coding"
        );

        add(
                result,
                "memory"
        );

        return result;
    }

    private void add(
            List<DomainScore> result,
            String domain
    ) {

        try {

            BenchmarkEngine.BenchmarkResult benchmark =
                    benchmarkEngine.run(
                            domain
                    );

            result.add(
                    new DomainScore(
                            domain,
                            benchmark.score()
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private String getImprovementTopic(
            String domain
    ) {

        return switch (domain) {

            case "knowledge" ->
                    "advanced knowledge retrieval, "
                            + "fact checking, information quality "
                            + "and knowledge organization";

            case "reasoning" ->
                    "logical reasoning, "
                            + "multi-step problem solving, "
                            + "deduction and inference";

            case "research" ->
                    "advanced web research, "
                            + "source discovery, source comparison, "
                            + "verification and information synthesis";

            case "coding" ->
                    "advanced software engineering, "
                            + "Java programming, debugging, "
                            + "architecture, algorithms and code quality";

            case "memory" ->
                    "advanced memory retrieval, "
                            + "context management, information recall "
                            + "and long-term knowledge organization";

            default ->
                    "general artificial intelligence "
                            + "reasoning and knowledge";
        };
    }

    public record DomainScore(
            String domain,
            int score
    ) {
    }

    public record ImprovementResult(
            String domain,
            int beforeScore,
            int afterScore,
            boolean improved,
            String message
    ) {
    }
}
