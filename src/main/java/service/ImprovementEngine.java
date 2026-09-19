package service;

import org.springframework.stereotype.Service;

@Service
public class ImprovementEngine {

    private final BenchmarkEngine benchmarkEngine;
    private final LearningEngine learningEngine;
    private final ImprovementPlanner improvementPlanner;
    private final ImprovementGenerator improvementGenerator;
    private final CodeChangeEngine codeChangeEngine;
    private final ImprovementVerifier improvementVerifier;
    private final CodeTestEngine codeTestEngine;

    public ImprovementEngine(
            BenchmarkEngine benchmarkEngine,
            LearningEngine learningEngine,
            ImprovementPlanner improvementPlanner,
            ImprovementGenerator improvementGenerator,
            CodeChangeEngine codeChangeEngine,
            ImprovementVerifier improvementVerifier,
            CodeTestEngine codeTestEngine
    ) {
        this.benchmarkEngine = benchmarkEngine;
        this.learningEngine = learningEngine;
        this.improvementPlanner = improvementPlanner;
        this.improvementGenerator = improvementGenerator;
        this.codeChangeEngine = codeChangeEngine;
        this.improvementVerifier = improvementVerifier;
        this.codeTestEngine = codeTestEngine;
    }

    public synchronized ImprovementResult improveWeakestDomain() {

        ImprovementPlanner.ImprovementPlan plan =
                improvementPlanner.createPlan();

        return executePlan(plan);
    }

    public synchronized ImprovementResult improveDomain(
            String domain
    ) {

        if (domain == null
                || domain.isBlank()) {

            return new ImprovementResult(
                    "",
                    0,
                    0,
                    0,
                    false,
                    "Domain is empty."
            );
        }

        BenchmarkEngine.BenchmarkResult benchmark =
                benchmarkEngine.run(domain);

        SelfImprovementFeatureBank.Feature feature =
                improvementPlanner
                        .getHighestPriorityFeature(
                                domain
                        );

        if (feature == null) {

            return new ImprovementResult(
                    domain,
                    benchmark.score(),
                    benchmark.score(),
                    0,
                    false,
                    "No incomplete feature is available."
            );
        }

        ImprovementPlanner.ImprovementPlan plan =
                new ImprovementPlanner.ImprovementPlan(
                        domain,
                        benchmark.score(),
                        Math.max(
                                0,
                                100 - benchmark.score()
                        ),
                        feature.priority(),
                        "Improve "
                                + feature.name(),
                        improvementPlanner
                                .getFeaturesForDomain(
                                        domain
                                ),
                        feature.name(),
                        feature.name()
                );

        return executePlan(plan);
    }

    private ImprovementResult executePlan(
            ImprovementPlanner.ImprovementPlan plan
    ) {

        if (plan == null) {

            return new ImprovementResult(
                    "",
                    0,
                    0,
                    0,
                    false,
                    "No improvement plan available."
            );
        }

        String domain =
                plan.targetDomain();

        int before =
                plan.currentScore();

        SelfImprovementFeatureBank.Feature feature =
                improvementPlanner
                        .findFeature(
                                plan.featureName()
                        );

        if (feature == null) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Feature could not be found."
            );
        }

        improvementPlanner
                .markFeatureStarted(
                        feature.name()
                );

        String researchResult;

        try {

            researchResult =
                    learningEngine.learn(
                            plan.researchTopic()
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Learning failed."
            );
        }

        if (researchResult == null
                || researchResult.isBlank()) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "No useful research result."
            );
        }

        ImprovementGenerator.ImprovementProposal proposal;

        try {

            proposal =
                    improvementGenerator.generate(
                            domain,
                            feature
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Improvement generation failed."
            );
        }

        if (!proposal.generated()) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "No improvement proposal generated."
            );
        }

        CodeChangeEngine.ChangeResult changes;

        try {

            changes =
                    codeChangeEngine.createChangeSet(
                            proposal
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Code change generation failed."
            );
        }

        if (!changes.success()) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Generated changes failed to enter sandbox."
            );
        }

        ImprovementVerifier.VerificationResult verification;

        try {

            verification =
                    improvementVerifier.verify(
                            changes.experimentId()
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Verification failed."
            );
        }

        if (!verification.passed()) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Safety verification failed."
            );
        }

        CodeTestEngine.TestResult tests;

        try {

            tests =
                    codeTestEngine.test(
                            changes.experimentId()
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Code tests failed to execute."
            );
        }

        if (!tests.passed()) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Generated code failed verification tests."
            );
        }

        BenchmarkEngine.BenchmarkResult afterResult;

        try {

            afterResult =
                    benchmarkEngine.run(
                            domain
                    );

        } catch (Exception e) {

            return new ImprovementResult(
                    domain,
                    before,
                    before,
                    0,
                    false,
                    "Post-improvement benchmark failed."
            );
        }

        int after =
                afterResult.score();

        int improvement =
                after - before;

        if (improvement > 0) {

            improvementPlanner
                    .markFeatureSuccessful(
                            feature.name()
                    );

            return new ImprovementResult(
                    domain,
                    before,
                    after,
                    improvement,
                    true,
                    "Improvement verified successfully."
            );
        }

        return new ImprovementResult(
                domain,
                before,
                after,
                improvement,
                false,
                "Change passed safety tests but did not improve the benchmark."
        );
    }

    public record ImprovementResult(
            String domain,
            int beforeScore,
            int afterScore,
            int improvement,
            boolean successful,
            String message
    ) {
    }
}
