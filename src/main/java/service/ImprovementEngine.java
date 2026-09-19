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
        this.benchmarkEngine =
                benchmarkEngine;

        this.learningEngine =
                learningEngine;

        this.improvementPlanner =
                improvementPlanner;
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
                            + e.getMessage(),
                    null
            );
        }

        return executePlan(plan);
    }

    public synchronized ImprovementResult improveDomain(
            String domain
    ) {

        if (domain == null
                || domain.isBlank()) {

            return new ImprovementResult(
                    "unknown",
                    0,
                    0,
                    false,
                    "Domain is empty.",
                    null
            );
        }

        ImprovementPlanner.ImprovementPlan plan;

        try {

            plan =
                    improvementPlanner.createPlan();

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    0,
                    0,
                    false,
                    "Could not create improvement plan: "
                            + e.getMessage(),
                    null
            );
        }

        BenchmarkEngine.BenchmarkResult benchmark;

        try {

            benchmark =
                    benchmarkEngine.run(
                            domain
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    0,
                    0,
                    false,
                    "Benchmark failed: "
                            + e.getMessage(),
                    null
            );
        }

        SelfImprovementFeatureBank.Feature feature =
                findFeatureForDomain(
                        domain
                );

        String topic;

        if (feature != null) {

            topic =
                    buildFeatureTopic(
                            feature
                    );

        } else {

            topic =
                    plan.researchTopic();
        }

        ImprovementPlanner.ImprovementPlan domainPlan =
                new ImprovementPlanner.ImprovementPlan(
                        domain,
                        benchmark.score(),
                        Math.max(
                                0,
                                100 - benchmark.score()
                        ),
                        calculateDifficulty(
                                benchmark.score()
                        ),
                        topic,
                        plan.availableDomains(),
                        feature,
                        feature == null
                                ? ""
                                : feature.name()
                );

        return executePlan(
                domainPlan
        );
    }

    private ImprovementResult executePlan(
            ImprovementPlanner.ImprovementPlan plan
    ) {

        if (plan.targetDomain() == null
                || plan.targetDomain().isBlank()) {

            return new ImprovementResult(
                    "unknown",
                    0,
                    0,
                    false,
                    "No improvement target found.",
                    plan.feature()
            );
        }

        String domain =
                plan.targetDomain();

        int before =
                plan.currentScore();

        String featureName =
                plan.featureName();

        if (plan.feature() != null) {

            featureName =
                    plan.feature()
                            .name();
        }

        String topic =
                plan.researchTopic();

        if (plan.feature() != null) {

            markFeatureStarted(
                    featureName
            );
        }

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
                            + e.getMessage(),
                    plan.feature()
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
                            + e.getMessage(),
                    plan.feature()
            );
        }

        int afterScore =
                after.score();

        boolean improved =
                afterScore > before;

        int improvement =
                afterScore - before;

        if (improved
                && plan.feature() != null) {

            markFeatureSuccessful(
                    featureName
            );
        }

        StringBuilder message =
                new StringBuilder();

        if (improved) {

            message.append(
                    "Domain improved."
            );

        } else {

            message.append(
                    "No measurable improvement."
            );
        }

        message.append(
                System.lineSeparator()
        )
        .append(
                "Domain: "
        )
        .append(
                domain
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Feature: "
        )
        .append(
                featureName.isBlank()
                        ? "General improvement"
                        : featureName
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Difficulty: "
        )
        .append(
                plan.difficulty()
        )
        .append(
                "/5"
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Benchmark: "
        )
        .append(
                before
        )
        .append(
                " → "
        )
        .append(
                afterScore
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Improvement: "
        )
        .append(
                improvement >= 0
                        ? "+"
                        : ""
        )
        .append(
                improvement
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Research:"
        )
        .append(
                System.lineSeparator()
        )
        .append(
                learningResult
        );

        return new ImprovementResult(
                domain,
                before,
                afterScore,
                improved,
                message.toString(),
                plan.feature()
        );
    }

    private SelfImprovementFeatureBank.Feature findFeatureForDomain(
            String domain
    ) {

        return improvementPlanner
                .getFeaturesForDomain(domain)
                .stream()
                .filter(
                        feature ->
                                !feature.completed()
                )
                .findFirst()
                .orElse(null);
    }

    private String buildFeatureTopic(
            SelfImprovementFeatureBank.Feature feature
    ) {

        return """
                Research how to improve Blackwater with this capability:

                Capability:
                %s

                Domain:
                %s

                Description:
                %s

                Priority:
                %d

                Determine the safest implementation approach.
                Identify required backend and frontend changes.
                Identify dependencies and security concerns.
                Identify automated tests needed to verify the change.
                """.formatted(
                feature.name(),
                feature.domain(),
                feature.description(),
                feature.priority()
        ).trim();
    }

    private int calculateDifficulty(
            int score
    ) {

        if (score < 20) {
            return 1;
        }

        if (score < 40) {
            return 2;
        }

        if (score < 60) {
            return 3;
        }

        if (score < 80) {
            return 4;
        }

        return 5;
    }

    private void markFeatureStarted(
            String featureName
    ) {

        if (featureName == null
                || featureName.isBlank()) {

            return;
        }

        try {

            /*
             * Feature progress is intentionally handled
             * by the feature bank.
             *
             * The planner remains responsible for
             * selecting the feature.
             */

        } catch (Exception ignored) {
        }
    }

    private void markFeatureSuccessful(
            String featureName
    ) {

        if (featureName == null
                || featureName.isBlank()) {

            return;
        }

        try {

            /*
             * Feature completion will be persisted
             * when the code-evolution layer is added.
             */

        } catch (Exception ignored) {
        }
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
            String message,
            SelfImprovementFeatureBank.Feature feature
    ) {
    }
}
