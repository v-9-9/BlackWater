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

    public synchronized ImprovementResult improveWeakestDomain() {

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

    public synchronized ImprovementResult improveDomain(
            String domain
    ) {

        if (domain == null || domain.isBlank()) {
            return improveWeakestDomain();
        }

        String normalized =
                normalizeDomain(domain);

        ImprovementPlanner.ImprovementPlan plan =
                buildDomainPlan(normalized);

        if (plan == null) {
            return failedResult(
                    normalized,
                    null,
                    "No improvement plan exists for domain: "
                            + normalized
            );
        }

        return executePlan(plan);
    }

    private ImprovementPlanner.ImprovementPlan buildDomainPlan(
            String domain
    ) {

        ImprovementPlanner.ImprovementPlan weakestPlan =
                improvementPlanner.createPlan();

        if (weakestPlan == null) {
            return null;
        }

        if (normalizeDomain(
                weakestPlan.targetDomain()
        ).equals(normalizeDomain(domain))) {
            return weakestPlan;
        }

        List<
                SelfImprovementFeatureBank.Feature>
                features =
                improvementPlanner.getFeaturesForDomain(
                        domain
                );

        if (features == null || features.isEmpty()) {
            return null;
        }

        SelfImprovementFeatureBank.Feature feature =
                features.stream()
                        .filter(
                                item ->
                                        item != null
                                                && !item.completed()
                        )
                        .max(
                                java.util.Comparator
                                        .comparingInt(
                                                SelfImprovementFeatureBank.Feature
                                                        ::priority
                                        )
                        )
                        .orElse(null);

        if (feature == null) {
            return null;
        }

        String researchTopic =
                """
                Research how to safely improve Blackwater's
                %s capability.

                Feature:
                %s

                Description:
                %s

                Focus on practical implementation,
                architecture, testing, security,
                regression prevention and measurable improvement.
                """
                        .formatted(
                                domain,
                                feature.name(),
                                feature.description()
                        )
                        .trim();

        double currentScore =
                benchmarkEngine
                        .run(domain)
                        .averageScore();

        int currentPercentage =
                (int) Math.round(
                        Math.max(
                                0.0,
                                Math.min(
                                        1.0,
                                        currentScore
                                )
                        ) * 100.0
                );

        return new ImprovementPlanner.ImprovementPlan(
                domain,
                currentPercentage,
                Math.max(
                        0,
                        100 - currentPercentage
                ),
                Math.max(
                        1,
                        feature.priority()
                ),
                researchTopic,
                features,
                feature.name(),
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

        String featureName =
                plan.featureName();

        SelfImprovementFeatureBank.Feature feature =
                improvementPlanner.findFeature(
                        featureName
                );

        if (feature == null) {
            return failedResult(
                    domain,
                    featureName,
                    "Improvement feature could not be found."
            );
        }

        improvementPlanner.markFeatureStarted(
                featureName
        );

        BenchmarkEngine.BenchmarkResult before =
                benchmarkEngine.run(domain);

        double beforeScore =
                before.averageScore();

        try {

            if (plan.researchTopic() != null
                    && !plan.researchTopic().isBlank()) {

                learningEngine.learn(
                        plan.researchTopic()
                );
            }

        } catch (Exception ignored) {
        }

        ImprovementGenerator.ImprovementProposal proposal;

        try {

            proposal =
                    improvementGenerator.generate(
                            domain,
                            feature
                    );

        } catch (Exception e) {

            return failedResult(
                    domain,
                    featureName,
                    "Improvement proposal generation failed: "
                            + safeMessage(e)
            );
        }

        if (proposal == null) {

            return failedResult(
                    domain,
                    featureName,
                    "Improvement proposal is null."
            );
        }

        if (!proposal.generated()) {

            return failedResult(
                    domain,
                    featureName,
                    "Improvement proposal was rejected: "
                            + proposal.rawProposal()
            );
        }

        CodeChangeEngine.ChangeResult changeResult;

        try {

            changeResult =
                    codeChangeEngine.createChangeSet(
                            proposal
                    );

        } catch (Exception e) {

            return failedResult(
                    domain,
                    featureName,
                    "Failed to create sandbox experiment: "
                            + safeMessage(e)
            );
        }

        if (changeResult == null
                || !changeResult.success()) {

            return failedResult(
                    domain,
                    featureName,
                    "Sandbox experiment creation failed: "
                            + (
                            changeResult == null
                                    ? "No result."
                                    : changeResult.message()
                    )
            );
        }

        String experimentId =
                changeResult.experimentId();

        if (experimentId == null
                || experimentId.isBlank()) {

            return failedResult(
                    domain,
                    featureName,
                    "Sandbox experiment ID was not created."
            );
        }

        CodeGenerationEngine.GenerationResult generation;

        try {

            generation =
                    codeGenerationEngine.generate(
                            proposal,
                            experimentId
                    );

        } catch (Exception e) {

            return failedResult(
                    domain,
                    featureName,
                    "Code generation failed: "
                            + safeMessage(e)
            );
        }

        if (generation == null
                || !generation.success()) {

            return failedResult(
                    domain,
                    featureName,
                    "Code generation was rejected: "
                            + summarizeErrors(
                            generation == null
                                    ? List.of(
                                    "No generation result."
                            )
                                    : generation.errors()
                    )
            );
        }

        List<String> generatedFiles =
                generation.generatedFiles();

        if (generatedFiles == null
                || generatedFiles.isEmpty()) {

            return failedResult(
                    domain,
                    featureName,
                    "No files were generated."
            );
        }

        ImprovementVerifier.VerificationResult verification;

        try {

            verification =
                    improvementVerifier.verify(
                            experimentId
                    );

        } catch (Exception e) {

            return failedResult(
                    domain,
                    featureName,
                    "Improvement verification failed: "
                            + safeMessage(e)
            );
        }

        if (verification == null
                || !verification.passed()) {

            return failedResult(
                    domain,
                    featureName,
                    "Generated change failed safety verification."
            );
        }

        CodeTestEngine.TestResult testResult;

        try {

            testResult =
                    codeTestEngine.test(
                            experimentId
                    );

        } catch (Exception e) {

            return failedResult(
                    domain,
                    featureName,
                    "Code tests failed to run: "
                            + safeMessage(e)
            );
        }

        if (testResult == null
                || !testResult.success()) {

            return failedResult(
                    domain,
                    featureName,
                    "Generated change failed code tests."
            );
        }

        CodeChangeApplier.ApplyResult applyResult;

        try {

            applyResult =
                    codeChangeApplier.apply(
                            experimentId,
                            generatedFiles
                    );

        } catch (Exception e) {

            return failedResult(
                    domain,
                    featureName,
                    "Failed to apply candidate change: "
                            + safeMessage(e)
            );
        }

        if (applyResult == null
                || !applyResult.success()) {

            return failedResult(
                    domain,
                    featureName,
                    "Candidate change could not be applied."
            );
        }

        String backupId =
                applyResult.backupId();

        BenchmarkEngine.BenchmarkResult after;

        try {

            after =
                    benchmarkEngine.run(domain);

        } catch (Exception e) {

            rollbackSafely(
                    backupId,
                    generatedFiles
            );

            return failedResult(
                    domain,
                    featureName,
                    "Post-change benchmark failed. "
                            + "Change rolled back."
            );
        }

        double afterScore =
                after.averageScore();

        double improvement =
                afterScore - beforeScore;

        if (improvement <= 0.0) {

            rollbackSafely(
                    backupId,
                    generatedFiles
            );

            return new ImprovementResult(
                    domain,
                    featureName,
                    experimentId,
                    false,
                    false,
                    beforeScore,
                    afterScore,
                    improvement,
                    generatedFiles,
                    "No measurable improvement. "
                            + "Change rolled back."
            );
        }

        improvementPlanner.markFeatureSuccessful(
                featureName
        );

        return new ImprovementResult(
                domain,
                featureName,
                experimentId,
                true,
                true,
                beforeScore,
                afterScore,
                improvement,
                generatedFiles,
                "Improvement verified, tested, "
                        + "benchmarked, and kept."
        );
    }

    private void rollbackSafely(
            String backupId,
            List<String> files
    ) {

        if (backupId == null
                || backupId.isBlank()) {
            return;
        }

        try {

            codeChangeApplier.rollback(
                    backupId,
                    files
            );

        } catch (Exception ignored) {
        }
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

    private String normalizeDomain(
            String domain
    ) {

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
            case "CODING",
                 "CODE",
                 "PROGRAMMING" -> "CODING";
            case "MEMORY" -> "MEMORY";
            default -> "CODING";
        };
    }

    private String summarizeErrors(
            List<String> errors
    ) {

        if (errors == null
                || errors.isEmpty()) {
            return "unknown generation error";
        }

        StringBuilder result =
                new StringBuilder();

        int limit =
                Math.min(
                        errors.size(),
                        3
                );

        for (int i = 0;
             i < limit;
             i++) {

            if (i > 0) {
                result.append(" | ");
            }

            result.append(
                    errors.get(i)
            );
        }

        return result.toString();
    }

    private String safeMessage(
            Exception e
    ) {

        if (e == null
                || e.getMessage() == null) {
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
