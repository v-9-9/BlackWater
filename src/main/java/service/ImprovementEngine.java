package service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

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
    private final CodeChangeApplier codeChangeApplier;

    public ImprovementEngine(
            BenchmarkEngine benchmarkEngine,
            LearningEngine learningEngine,
            ImprovementPlanner improvementPlanner,
            ImprovementGenerator improvementGenerator,
            CodeChangeEngine codeChangeEngine,
            CodeGenerationEngine codeGenerationEngine,
            ImprovementVerifier improvementVerifier,
            CodeTestEngine codeTestEngine,
            CodeChangeApplier codeChangeApplier
    ) {
        this.benchmarkEngine = benchmarkEngine;
        this.learningEngine = learningEngine;
        this.improvementPlanner = improvementPlanner;
        this.improvementGenerator = improvementGenerator;
        this.codeChangeEngine = codeChangeEngine;
        this.codeGenerationEngine = codeGenerationEngine;
        this.improvementVerifier = improvementVerifier;
        this.codeTestEngine = codeTestEngine;
        this.codeChangeApplier = codeChangeApplier;
    }

    public ImprovementResult improveWeakestDomain() {
        ImprovementPlanner.ImprovementPlan plan =
                improvementPlanner.createPlan();

        if (plan == null) {
            return failedResult(
                    null,
                    null,
                    "No improvement plan could be created."
            );
        }

        return executePlan(plan);
    }

    public ImprovementResult improveDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return improveWeakestDomain();
        }

        String normalized = normalizeDomain(domain);

        ImprovementPlanner.ImprovementPlan plan =
                improvementPlanner.createPlanForDomain(normalized);

        if (plan == null) {
            return failedResult(
                    normalized,
                    null,
                    "No improvement plan exists for domain: " + normalized
            );
        }

        return executePlan(plan);
    }

    private ImprovementResult executePlan(
            ImprovementPlanner.ImprovementPlan plan
    ) {
        String domain = normalizeDomain(plan.targetDomain());
        String feature = plan.featureName();

        improvementPlanner.markFeatureStarted(feature);

        /*
         * 1. Benchmark BEFORE making any change.
         */
        BenchmarkEngine.BenchmarkResult before =
                benchmarkEngine.run(domain);

        double beforeScore = before.averageScore();

        /*
         * 2. Research and learn information relevant to the target.
         */
        try {
            if (plan.researchTopic() != null
                    && !plan.researchTopic().isBlank()) {

                learningEngine.learn(plan.researchTopic());
            }
        } catch (Exception ignored) {
            /*
             * Learning failure must not destroy the entire evolution cycle.
             * The generated improvement can still be evaluated safely.
             */
        }

        /*
         * 3. Generate an improvement proposal.
         */
        ImprovementGenerator.ImprovementProposal proposal;

        try {
            proposal = improvementGenerator.generate(
                    domain,
                    feature
            );
        } catch (Exception e) {
            return failedResult(
                    domain,
                    feature,
                    "Improvement proposal generation failed: "
                            + safeMessage(e)
            );
        }

        if (proposal == null) {
            return failedResult(
                    domain,
                    feature,
                    "Improvement proposal is null."
            );
        }

        /*
         * 4. Create the single sandbox experiment.
         *
         * CodeGenerationEngine will use this SAME experiment.
         */
        String experimentId;

        try {
            experimentId =
                    codeChangeEngine.createChangeSet(proposal);
        } catch (Exception e) {
            return failedResult(
                    domain,
                    feature,
                    "Failed to create sandbox experiment: "
                            + safeMessage(e)
            );
        }

        if (experimentId == null || experimentId.isBlank()) {
            return failedResult(
                    domain,
                    feature,
                    "Sandbox experiment ID was not created."
            );
        }

        /*
         * 5. Generate actual replacement files inside that experiment.
         */
        CodeGenerationEngine.GenerationResult generation;

        try {
            generation = codeGenerationEngine.generate(
                    proposal,
                    experimentId
            );
        } catch (Exception e) {
            return failedResult(
                    domain,
                    feature,
                    "Code generation failed: "
                            + safeMessage(e)
            );
        }

        if (generation == null || !generation.success()) {
            return failedResult(
                    domain,
                    feature,
                    "Code generation was rejected: "
                            + summarizeErrors(
                            generation == null
                                    ? List.of("No generation result.")
                                    : generation.errors()
                    )
            );
        }

        List<String> generatedFiles =
                generation.generatedFiles();

        if (generatedFiles == null || generatedFiles.isEmpty()) {
            return failedResult(
                    domain,
                    feature,
                    "No files were generated."
            );
        }

        /*
         * 6. Safety verification.
         */
        ImprovementVerifier.VerificationResult verification;

        try {
            verification =
                    improvementVerifier.verify(experimentId);
        } catch (Exception e) {
            return failedResult(
                    domain,
                    feature,
                    "Improvement verification failed: "
                            + safeMessage(e)
            );
        }

        if (verification == null || !verification.safe()) {
            return failedResult(
                    domain,
                    feature,
                    "Generated change failed safety verification."
            );
        }

        /*
         * 7. Structural code tests.
         */
        CodeTestEngine.TestResult testResult;

        try {
            testResult =
                    codeTestEngine.test(experimentId);
        } catch (Exception e) {
            return failedResult(
                    domain,
                    feature,
                    "Code tests failed to run: "
                            + safeMessage(e)
            );
        }

        if (testResult == null || !testResult.success()) {
            return failedResult(
                    domain,
                    feature,
                    "Generated change failed code tests."
            );
        }

        /*
         * 8. Benchmark the generated improvement BEFORE applying it.
         *
         * Important:
         * The benchmark implementation must evaluate the experiment
         * rather than blindly trusting generated code.
         *
         * Until the benchmark engine becomes experiment-aware,
         * this score represents the current system state.
         */
        BenchmarkEngine.BenchmarkResult generatedScore =
                benchmarkEngine.run(domain);

        double afterScore = generatedScore.averageScore();
        double improvement = afterScore - beforeScore;

        /*
         * 9. Only apply code when the measured score improved.
         *
         * No improvement = no live project modification.
         */
        if (improvement <= 0.0) {
            return new ImprovementResult(
                    domain,
                    feature,
                    experimentId,
                    false,
                    false,
                    beforeScore,
                    afterScore,
                    improvement,
                    generatedFiles,
                    "Improvement did not exceed the previous benchmark."
            );
        }

        CodeChangeApplier.ApplyResult applyResult;

        try {
            applyResult = codeChangeApplier.apply(
                    experimentId,
                    generatedFiles
            );
        } catch (Exception e) {
            return failedResult(
                    domain,
                    feature,
                    "Failed to apply verified improvement: "
                            + safeMessage(e)
            );
        }

        if (applyResult == null || !applyResult.success()) {
            return new ImprovementResult(
                    domain,
                    feature,
                    experimentId,
                    false,
                    false,
                    beforeScore,
                    afterScore,
                    improvement,
                    generatedFiles,
                    "Improvement was measured but could not be applied."
            );
        }

        /*
         * 10. Mark feature successful only after live application.
         */
        improvementPlanner.markFeatureSuccessful(feature);

        return new ImprovementResult(
                domain,
                feature,
                experimentId,
                true,
                true,
                beforeScore,
                afterScore,
                improvement,
                generatedFiles,
                "Improvement verified, benchmarked, and applied."
        );
    }

    private ImprovementResult failedResult(
            String domain,
            String feature,
            String message
    ) {
        return new ImprovementResult(
                domain,
                feature,
                null,
                false,
                false,
                0.0,
                0.0,
                0.0,
                List.of(),
                message
        );
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

    private String summarizeErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "unknown generation error";
        }

        StringBuilder result = new StringBuilder();

        int limit = Math.min(errors.size(), 3);

        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                result.append(" | ");
            }

            result.append(errors.get(i));
        }

        return result.toString();
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null) {
            return "unknown error";
        }

        return e.getMessage()
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    public record ImprovementResult(
            String domain,
            String feature,
            String experimentId,
            boolean successful,
            boolean applied,
            double beforeScore,
            double afterScore,
            double improvement,
            List<String> generatedFiles,
            String message
    ) {
    }
}
