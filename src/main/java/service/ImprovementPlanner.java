package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImprovementPlanner {

    private final BenchmarkEngine benchmarkEngine;
    private final SelfImprovementFeatureBank featureBank;

    public ImprovementPlanner(
            BenchmarkEngine benchmarkEngine,
            SelfImprovementFeatureBank featureBank
    ) {
        this.benchmarkEngine = benchmarkEngine;
        this.featureBank = featureBank;
    }

    public synchronized ImprovementPlan createPlan() {

        BenchmarkEngine.BenchmarkSummary summary =
                benchmarkEngine.runAll();

        Map<String, Double> domainScores =
                summary.domainScores() == null
                        ? Map.of()
                        : summary.domainScores();

        List<DomainScore> scores =
                List.of(
                        createDomainScore(
                                "knowledge",
                                domainScores
                        ),
                        createDomainScore(
                                "reasoning",
                                domainScores
                        ),
                        createDomainScore(
                                "research",
                                domainScores
                        ),
                        createDomainScore(
                                "coding",
                                domainScores
                        ),
                        createDomainScore(
                                "memory",
                                domainScores
                        )
                );

        DomainScore weakest =
                scores.stream()
                        .min(
                                Comparator.comparingInt(
                                        DomainScore::score
                                )
                        )
                        .orElse(
                                new DomainScore(
                                        "knowledge",
                                        0
                                )
                        );

        SelfImprovementFeatureBank.Feature feature =
                getHighestPriorityFeature(
                        weakest.domain()
                );

        if (feature == null) {

            return new ImprovementPlan(
                    weakest.domain(),
                    weakest.score(),
                    Math.max(
                            0,
                            100 - weakest.score()
                    ),
                    1,
                    "Improve the "
                            + weakest.domain()
                            + " capability.",
                    getFeaturesForDomain(
                            weakest.domain()
                    ),
                    "",
                    ""
            );
        }

        return new ImprovementPlan(
                weakest.domain(),
                weakest.score(),
                Math.max(
                        0,
                        100 - weakest.score()
                ),
                Math.max(
                        1,
                        feature.priority()
                ),
                buildResearchTopic(
                        weakest.domain(),
                        feature
                ),
                getFeaturesForDomain(
                        weakest.domain()
                ),
                feature.name(),
                feature.name()
        );
    }

    private DomainScore createDomainScore(
            String domain,
            Map<String, Double> domainScores
    ) {

        double normalized =
                domainScores.getOrDefault(
                        domain.toUpperCase(Locale.ROOT),
                        0.0
                );

        int percentage =
                (int) Math.round(
                        Math.max(
                                0.0,
                                Math.min(
                                        1.0,
                                        normalized
                                )
                        ) * 100.0
                );

        return new DomainScore(
                domain,
                percentage
        );
    }

    public synchronized SelfImprovementFeatureBank.Feature
    getHighestPriorityFeature(
            String domain
    ) {

        List<SelfImprovementFeatureBank.Feature> features =
                getFeaturesForDomain(domain);

        return features.stream()
                .filter(
                        feature ->
                                !feature.completed()
                )
                .max(
                        Comparator.comparingInt(
                                SelfImprovementFeatureBank.Feature
                                        ::priority
                        )
                )
                .orElse(null);
    }

    public synchronized SelfImprovementFeatureBank.Feature
    findFeature(
            String featureName
    ) {

        return featureBank.find(
                featureName
        );
    }

    public synchronized void markFeatureStarted(
            String featureName
    ) {

        if (featureName == null
                || featureName.isBlank()) {
            return;
        }

        featureBank.markStarted(
                featureName
        );
    }

    public synchronized void markFeatureSuccessful(
            String featureName
    ) {

        if (featureName == null
                || featureName.isBlank()) {
            return;
        }

        featureBank.markSuccessful(
                featureName
        );
    }

    public synchronized List<
            SelfImprovementFeatureBank.Feature>
    getFeaturesForDomain(
            String domain
    ) {

        if (domain == null
                || domain.isBlank()) {
            return List.of();
        }

        return new ArrayList<>(
                featureBank.getByDomain(
                        normalizeDomain(domain)
                )
        );
    }

    public synchronized List<
            SelfImprovementFeatureBank.Feature>
    getAvailableFeatures() {

        return new ArrayList<>(
                featureBank.getAll()
        );
    }

    public synchronized int totalFeatures() {
        return featureBank.totalFeatures();
    }

    public synchronized int completedFeatures() {
        return featureBank.completedFeatures();
    }

    public synchronized String previewPlan() {

        ImprovementPlan plan =
                createPlan();

        return """
                Target domain: %s
                Current score: %d
                Gap: %d
                Difficulty: %d
                Feature: %s
                Research topic: %s
                """
                .formatted(
                        plan.targetDomain(),
                        plan.currentScore(),
                        plan.gap(),
                        plan.difficulty(),
                        plan.featureName(),
                        plan.researchTopic()
                )
                .trim();
    }

    private String buildResearchTopic(
            String domain,
            SelfImprovementFeatureBank.Feature feature
    ) {

        return """
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
                        normalizeDomain(domain),
                        feature.name(),
                        feature.description()
                )
                .trim();
    }

    private String normalizeDomain(
            String domain
    ) {

        return domain
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public record ImprovementPlan(
            String targetDomain,
            int currentScore,
            int gap,
            int difficulty,
            String researchTopic,
            List<
                    SelfImprovementFeatureBank.Feature>
                    availableDomains,
            String feature,
            String featureName
    ) {
    }

    private record DomainScore(
            String domain,
            int score
    ) {
    }
}
