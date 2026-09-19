package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ImprovementPlanner {

    private final BenchmarkEngine benchmarkEngine;

    public ImprovementPlanner(
            BenchmarkEngine benchmarkEngine
    ) {
        this.benchmarkEngine =
                benchmarkEngine;
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
                    List.of()
            );
        }

        Capability weakest =
                capabilities.stream()
                        .min(
                                Comparator
                                        .comparingInt(
                                                Capability::score
                                        )
                        )
                        .orElse(null);

        if (weakest == null) {

            return new ImprovementPlan(
                    "unknown",
                    0,
                    0,
                    0,
                    "Could not identify a target.",
                    List.of()
            );
        }

        int gap =
                Math.max(
                        0,
                        100 - weakest.score()
                );

        int difficulty =
                calculateDifficulty(
                        weakest.score()
                );

        String topic =
                getTopic(
                        weakest.domain(),
                        difficulty
                );

        return new ImprovementPlan(
                weakest.domain(),
                weakest.score(),
                gap,
                difficulty,
                topic,
                capabilities
                        .stream()
                        .map(
                                Capability::domain
                        )
                        .toList()
        );
    }

    private List<Capability> collectCapabilities() {

        List<Capability> result =
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

        String level =
                switch (difficulty) {

                    case 1 ->
                            "fundamentals";

                    case 2 ->
                            "intermediate concepts";

                    case 3 ->
                            "advanced concepts";

                    case 4 ->
                            "expert-level concepts";

                    default ->
                            "research-frontier concepts";
                };

        return switch (domain) {

            case "knowledge" ->
                    "knowledge retrieval, "
                            + "fact verification and "
                            + level;

            case "reasoning" ->
                    "logical reasoning, "
                            + "multi-step inference and "
                            + level;

            case "research" ->
                    "web research, "
                            + "source comparison, "
                            + "verification and "
                            + level;

            case "coding" ->
                    "software engineering, "
                            + "algorithms, debugging, "
                            + "architecture and "
                            + level;

            case "memory" ->
                    "memory retrieval, "
                            + "context management, "
                            + "long-term information organization "
                            + "and "
                            + level;

            default ->
                    "general artificial intelligence "
                            + level;
        };
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
            List<String> availableDomains
    ) {
    }
}
