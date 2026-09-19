package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImprovementEngine {

    private final BenchmarkEngine benchmarkEngine;
    private final LearningEngine learningEngine;
    private final ImprovementPlanner improvementPlanner;

    public ImprovementEngine(
            BenchmarkEngine benchmarkEngine,
            LearningEngine learningEngine,
            ImprovementPlanner improvementPlanner
    ) {
        this.benchmarkEngine = benchmarkEngine;
        this.learningEngine = learningEngine;
        this.improvementPlanner = improvementPlanner;
    }

    public synchronized ImprovementResult improveWeakestDomain() {

        ImprovementPlanner.ImprovementPlan plan;

        try {

            plan =
                    improvementPlanner.createPlan();

        } catch (Exception e) {

            return new ImprovementResult(
                    "unknown",
                    0,
                    0,
                    false,
                    "Could not create improvement plan: "
                            + e.getMessage()
            );
        }

        if (plan.targetDomain() == null
                || plan.targetDomain().isBlank()) {

            return new ImprovementResult(
                    "unknown",
                    0,
                    0,
                    false,
                    "No improvement target found."
            );
        }

        String domain =
                plan.targetDomain();

        int before =
                plan.currentScore();

        String topic =
                plan.researchTopic();

        String learningResult;

        try {

            learningResult =
                    learningEngine.learn(
                            topic
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    false,
                    "Learning failed: "
                            + e.getMessage()
            );
        }

        BenchmarkEngine.BenchmarkResult after;

        try {

            after =
                    benchmarkEngine.run(
                            domain
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    false,
                    "Post-learning benchmark failed: "
                            + e.getMessage()
            );
        }

        int afterScore =
                after.score();

        boolean improved =
                afterScore > before;

        int improvement =
                afterScore - before;

        String message;

        if (improved) {

            message =
                    "Domain improved."
                            + System.lineSeparator()
                            + "Domain: "
                            + domain
                            + System.lineSeparator()
                            + "Difficulty: "
                            + plan.difficulty()
                            + "/5"
                            + System.lineSeparator()
                            + "Benchmark: "
                            + before
                            + " → "
                            + afterScore
                            + System.lineSeparator()
                            + "Improvement: +"
                            + improvement;

        } else {

            message =
                    "No measurable improvement."
                            + System.lineSeparator()
                            + "Domain: "
                            + domain
                            + System.lineSeparator()
                            + "Benchmark: "
                            + before
                            + " → "
                            + afterScore;
        }

        return new ImprovementResult(
                domain,
                before,
                afterScore,
                improved,
                message
                        + System.lineSeparator()
                        + "Research:"
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
