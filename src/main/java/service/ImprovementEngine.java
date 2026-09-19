package service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ImprovementEngine {

    private final BenchmarkEngine benchmarkEngine;
    private final LearningEngine learningEngine;
    private final ImprovementPlanner improvementPlanner;
    private final ImprovementGenerator improvementGenerator;
    private final CodeChangeEngine codeChangeEngine;
    private final CodeGenerationEngine codeGenerationEngine;
    private final ImprovementVerifier improvementVerifier;
    private final CodeTestEngine codeTestEngine;

    public ImprovementEngine(
            BenchmarkEngine benchmarkEngine,
            LearningEngine learningEngine,
            ImprovementPlanner improvementPlanner,
            ImprovementGenerator improvementGenerator,
            CodeChangeEngine codeChangeEngine,
            CodeGenerationEngine codeGenerationEngine,
            ImprovementVerifier improvementVerifier,
            CodeTestEngine codeTestEngine
    ) {
        this.benchmarkEngine = benchmarkEngine;
        this.learningEngine = learningEngine;
        this.improvementPlanner = improvementPlanner;
        this.improvementGenerator = improvementGenerator;
        this.codeChangeEngine = codeChangeEngine;
        this.codeGenerationEngine = codeGenerationEngine;
        this.improvementVerifier = improvementVerifier;
        this.codeTestEngine = codeTestEngine;
    }

    public ImprovementResult improveWeakestDomain() {
        ImprovementPlanner.ImprovementPlan plan =
                improvementPlanner.createPlan();

        if (plan == null) {
            return ImprovementResult.failed(
                    "",
                    "No improvement plan was created."
            );
        }

        return executePlan(plan);
    }

    public ImprovementResult improveDomain(
            String domain
    ) {
        String normalized =
                normalizeDomain(domain);

        if (normalized.isBlank()) {
            return ImprovementResult.failed(
                    "",
                    "Invalid improvement domain."
            );
        }

        ImprovementPlanner.ImprovementPlan plan =
                createDomainPlan(normalized);

        if (plan == null) {
            return ImprovementResult.failed(
                    normalized,
                    "No incomplete feature is available for this domain."
            );
        }

        return executePlan(plan);
    }

    private ImprovementPlanner.ImprovementPlan createDomainPlan(
            String domain
    ) {
        BenchmarkEngine.BenchmarkSummary benchmark =
                benchmarkEngine.run(domain);

        SelfImprovementFeatureBank.Feature feature =
                improvementPlanner
                        .getHighestPriorityFeature(domain);

        if (feature == null) {
            return null;
        }

        double score =
                benchmark == null
                        ? 0.0
                        : benchmark.score();

        double gap =
                Math.max(
                        0.0,
                        1.0 - score
                );

        String researchTopic =
                buildResearchTopic(
                        domain,
                        feature.name()
                );

        return new ImprovementPlanner.ImprovementPlan(
                domain,
                score,
                gap,
                feature.priority(),
                researchTopic,
                List.of(domain),
                feature,
                feature.name()
        );
    }

    private ImprovementResult executePlan(
            ImprovementPlanner.ImprovementPlan plan
    ) {
        String domain =
                normalizeDomain(
                        plan.targetDomain()
                );

        SelfImprovementFeatureBank.Feature feature =
                plan.feature();

        if (feature == null) {
            return ImprovementResult.failed(
                    domain,
                    "Plan does not contain a feature."
            );
        }

        improvementPlanner.markFeatureStarted(
                feature.id()
        );

        try {
            /*
             * STEP 1
             * Research the improvement target.
             */
            LearningEngine.LearningResult learning =
                    learningEngine.learn(
                            plan.researchTopic()
                    );

            /*
             * STEP 2
             * Generate a structured improvement proposal.
             */
            ImprovementGenerator.ImprovementProposal proposal =
                    improvementGenerator.generate(
                            domain,
                            feature
                    );

            if (proposal == null) {
                return ImprovementResult.failed(
                        domain,
                        "Improvement proposal generation failed."
                );
            }

            /*
             * STEP 3
             * Create the original sandbox change record.
             */
            CodeChangeEngine.ChangeSet changeSet =
                    codeChangeEngine.createChangeSet(
                            proposal
                    );

            if (changeSet == null
                    || changeSet.experimentId() == null
                    || changeSet.experimentId().isBlank()) {
                return ImprovementResult.failed(
                        domain,
                        "Could not create sandbox change set."
                );
            }

            String experimentId =
                    changeSet.experimentId();

            /*
             * STEP 4
             * Generate actual replacement code
             * inside the sandbox.
             *
             * This is where UI coding is now included.
             */
            CodeGenerationEngine.GenerationResult generation =
                    codeGenerationEngine.generate(
                            proposal
                    );

            if (generation == null
                    || !generation.success()) {

                return ImprovementResult.failed(
                        domain,
                        experimentId,
                        "Code generation failed: "
                                + joinErrors(
                                generation == null
                                        ? null
                                        : generation.errors()
                        )
                );
            }

            /*
             * STEP 5
             * Verify the generated files.
             */
            ImprovementVerifier.VerificationResult verification =
                    improvementVerifier.verify(
                            experimentId
                    );

            if (verification == null
                    || !verification.safe()) {

                return ImprovementResult.failed(
                        domain,
                        experimentId,
                        "Sandbox verification failed."
                );
            }

            /*
             * STEP 6
             * Test the sandbox.
             */
            CodeTestEngine.TestResult test =
                    codeTestEngine.test(
                            experimentId
                    );

            if (test == null
                    || !test.success()) {

                return ImprovementResult.failed(
                        domain,
                        experimentId,
                        "Sandbox tests failed."
                );
            }

            /*
             * STEP 7
             * Benchmark again.
             */
            BenchmarkEngine.BenchmarkSummary before =
                    benchmarkEngine.run(domain);

            BenchmarkEngine.BenchmarkSummary after =
                    benchmarkEngine.run(domain);

            double beforeScore =
                    before == null
                            ? 0.0
                            : before.score();

            double afterScore =
                    after == null
                            ? 0.0
                            : after.score();

            double improvement =
                    afterScore - beforeScore;

            /*
             * The generated code is NOT automatically
             * copied into the live project yet.
             *
             * Only a verified and tested experiment is
             * considered eligible for adoption.
             */
            if (improvement > 0) {
                improvementPlanner.markFeatureSuccessful(
                        feature.id()
                );
            }

            String message;

            if (improvement > 0) {
                message =
                        "Improvement verified successfully. "
                                + "Sandbox experiment is eligible for adoption.";
            } else {
                message =
                        "Experiment completed, but benchmark "
                                + "did not improve.";
            }

            return new ImprovementResult(
                    domain,
                    experimentId,
                    true,
                    improvement > 0,
                    beforeScore,
                    afterScore,
                    generation.generatedFiles(),
                    message
            );

        } catch (Exception exception) {
            return ImprovementResult.failed(
                    domain,
                    "Improvement cycle failed: "
                            + safe(
                            exception.getMessage()
                    )
            );
        }
    }

    private String buildResearchTopic(
            String domain,
            String feature
    ) {
        if ("coding".equals(domain)) {
            return "Improve Blackwater coding capability for feature: "
                    + feature
                    + ". Include backend Java, HTML, CSS, JavaScript, "
                    + "responsive UI and safe code generation where relevant.";
        }

        return "Improve Blackwater "
                + domain
                + " capability for feature: "
                + feature;
    }

    private String normalizeDomain(
            String domain
    ) {
        if (domain == null) {
            return "";
        }

        String value =
                domain
                        .trim()
                        .toLowerCase();

        return switch (value) {
            case "knowledge" -> "knowledge";
            case "reasoning" -> "reasoning";
            case "research" -> "research";
            case "coding",
                 "code",
                 "programming",
                 "ui",
                 "frontend",
                 "backend" -> "coding";
            case "memory" -> "memory";
            default -> "";
        };
    }

    private String joinErrors(
            List<String> errors
    ) {
        if (errors == null
                || errors.isEmpty()) {
            return "unknown generation error";
        }

        return String.join(
                " | ",
                errors
        );
    }

    private String safe(
            String value
    ) {
        return value == null
                ? "unknown error"
                : value;
    }

    public record ImprovementResult(
            String domain,
            String experimentId,
            boolean completed,
            boolean improved,
            double beforeScore,
            double afterScore,
            List<String> generatedFiles,
            String message
    ) {

        public static ImprovementResult failed(
                String domain,
                String message
        ) {
            return new ImprovementResult(
                    domain,
                    "",
                    false,
                    false,
                    0.0,
                    0.0,
                    List.of(),
                    message
            );
        }

        public static ImprovementResult failed(
                String domain,
                String experimentId,
                String message
        ) {
            return new ImprovementResult(
                    domain,
                    experimentId,
                    false,
                    false,
                    0.0,
                    0.0,
                    List.of(),
                    message
            );
        }
    }
}
