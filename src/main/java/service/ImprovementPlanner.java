package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ImprovementPlanner {

    private final BenchmarkEngine benchmarkEngine;
    private final SelfImprovementFeatureBank featureBank;

    public ImprovementPlanner(
            BenchmarkEngine benchmarkEngine,
            SelfImprovementFeatureBank featureBank
    ) {
        this.benchmarkEngine =
                benchmarkEngine;

        this.featureBank =
                featureBank;
    }

    public synchronized ImprovementPlan createPlan() {

        List<Capability> capabilities =
                collectCapabilities();

        if (capabilities.isEmpty()) {

            return new ImprovementPlan(
                    "unknown",
                    0,
                    0,
                    0,
                    "No benchmark data available.",
                    List.of(),
                    null,
                    ""
            );
        }

        Capability target =
                selectTarget(capabilities);

        if (target == null) {

            return new ImprovementPlan(
                    "unknown",
                    0,
                    0,
                    0,
                    "Could not identify an improvement target.",
                    List.of(),
                    null,
                    ""
            );
        }

        int gap =
                Math.max(
                        0,
                        100 - target.score()
                );

        int difficulty =
                calculateDifficulty(
                        target.score()
                );

        SelfImprovementFeatureBank.Feature feature =
                selectFeature(
                        target.domain()
                );

        String topic;

        String featureName = "";

        if (feature != null) {

            featureName =
                    feature.name();

            topic =
                    buildFeatureResearchTopic(
                            feature
                    );

        } else {

            topic =
                    getTopic(
                            target.domain(),
                            difficulty
                    );
        }

        return new ImprovementPlan(
                target.domain(),
                target.score(),
                gap,
                difficulty,
                topic,
                capabilities
                        .stream()
                        .map(
                                Capability::domain
                        )
                        .toList(),
                feature,
                featureName
        );
    }

    private List<Capability> collectCapabilities() {

        List<Capability> result =
                new ArrayList<>();

        addCapability(
                result,
                "knowledge"
        );

        addCapability(
                result,
                "reasoning"
        );

        addCapability(
                result,
                "research"
        );

        addCapability(
                result,
                "coding"
        );

        addCapability(
                result,
                "memory"
        );

        return result;
    }

    private void addCapability(
            List<Capability> result,
            String domain
    ) {

        try {

            BenchmarkEngine.BenchmarkResult benchmark =
                    benchmarkEngine.run(
                            domain
                    );

            result.add(
                    new Capability(
                            domain,
                            benchmark.score()
                    )
            );

        } catch (Exception ignored) {
        }
    }

    private Capability selectTarget(
            List<Capability> capabilities
    ) {

        return capabilities.stream()
                .min(
                        Comparator
                                .comparingInt(
                                        Capability::score
                                )
                )
                .orElse(null);
    }

    private SelfImprovementFeatureBank.Feature selectFeature(
            String domain
    ) {

        SelfImprovementFeatureBank.Feature feature =
                featureBank.getHighestPriority(
                        domain
                );

        if (feature != null) {
            return feature;
        }

        return featureBank
                .getHighestPriorityFeatures(1)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String buildFeatureResearchTopic(
            SelfImprovementFeatureBank.Feature feature
    ) {

        return """
                Research how to implement the following Blackwater capability.

                Capability:
                %s

                Domain:
                %s

                Description:
                %s

                Priority:
                %d

                The research should identify:
                - required backend changes
                - required frontend changes
                - required dependencies
                - security considerations
                - testing requirements
                - compatibility risks
                - a safe implementation approach
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

    private String getTopic(
            String domain,
            int difficulty
    ) {

        return switch (domain) {

            case "knowledge" ->
                    "Improve knowledge retrieval, "
                            + "verification and deduplication "
                            + "at difficulty "
                            + difficulty;

            case "reasoning" ->
                    "Improve multi-step reasoning, "
                            + "verification and problem solving "
                            + "at difficulty "
                            + difficulty;

            case "research" ->
                    "Improve multi-source web research, "
                            + "source comparison and verification "
                            + "at difficulty "
                            + difficulty;

            case "coding" ->
                    "Improve code generation, analysis, "
                            + "testing and safe code evolution "
                            + "at difficulty "
                            + difficulty;

            case "memory" ->
                    "Improve long-term memory, relevance, "
                            + "consolidation and recall "
                            + "at difficulty "
                            + difficulty;

            default ->
                    "Improve Blackwater capabilities "
                            + "at difficulty "
                            + difficulty;
        };
    }

    public synchronized List<SelfImprovementFeatureBank.Feature>
    getAvailableFeatures() {

        return featureBank.getAll();
    }

    public synchronized List<SelfImprovementFeatureBank.Feature>
    getFeaturesForDomain(
            String domain
    ) {

        return featureBank.getByDomain(
                domain
        );
    }

    public synchronized int totalFeatures() {

        return featureBank.totalFeatures();
    }

    public synchronized int completedFeatures() {

        return featureBank.completedFeatures();
    }

    public synchronized ImprovementPlan previewPlan() {

        return createPlan();
    }

    public record Capability(
            String domain,
            int score
    ) {
    }

    public record ImprovementPlan(
            String targetDomain,
            int currentScore,
            int gap,
            int difficulty,
            String researchTopic,
            List<String> availableDomains,
            SelfImprovementFeatureBank.Feature feature,
            String featureName
    ) {
    }
}
