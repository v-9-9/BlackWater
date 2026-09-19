package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SelfImprovementFeatureBank {

    private final List<Feature> features =
            new ArrayList<>();

    public SelfImprovementFeatureBank() {

        add(
                "ui",
                "Adaptive UI",
                "Improve the interface layout, responsiveness, animations and usability.",
                70
        );

        add(
                "ui",
                "Advanced UI Settings",
                "Add configurable themes, layouts, appearance settings and interface preferences.",
                60
        );

        add(
                "ui",
                "Conversation UI",
                "Improve conversation navigation, message rendering, code blocks and rich responses.",
                65
        );

        add(
                "voice",
                "Voice Input",
                "Allow the user to speak to Blackwater and convert speech into text.",
                90
        );

        add(
                "voice",
                "Voice Output",
                "Allow Blackwater to generate spoken responses.",
                85
        );

        add(
                "voice",
                "Continuous Voice Conversation",
                "Support natural back-and-forth voice conversations.",
                95
        );

        add(
                "vision",
                "Image Input",
                "Allow Blackwater to receive and analyze images.",
                90
        );

        add(
                "vision",
                "Image Understanding",
                "Extract useful information from screenshots, diagrams and photographs.",
                95
        );

        add(
                "files",
                "File Upload",
                "Allow users to send documents and other files to Blackwater.",
                90
        );

        add(
                "files",
                "Document Understanding",
                "Read, analyze and extract useful information from uploaded documents.",
                95
        );

        add(
                "files",
                "File Knowledge",
                "Store useful information learned from uploaded files in the knowledge system.",
                85
        );

        add(
                "reasoning",
                "Deep Reasoning",
                "Increase reasoning depth by breaking difficult problems into smaller verifiable steps.",
                100
        );

        add(
                "reasoning",
                "Multi-Step Reasoning",
                "Solve problems that require multiple dependent reasoning stages.",
                100
        );

        add(
                "reasoning",
                "Self Verification",
                "Check generated answers for contradictions, missing information and logical errors.",
                100
        );

        add(
                "reasoning",
                "Alternative Solutions",
                "Generate and compare multiple possible approaches before selecting a response.",
                90
        );

        add(
                "research",
                "Multi-Source Research",
                "Search multiple independent sources and combine relevant information.",
                100
        );

        add(
                "research",
                "Source Comparison",
                "Compare conflicting information and identify differences between sources.",
                100
        );

        add(
                "research",
                "Research Verification",
                "Verify important findings against additional sources before storing them.",
                100
        );

        add(
                "research",
                "Research Gap Detection",
                "Detect missing information and automatically perform additional research.",
                100
        );

        add(
                "coding",
                "Code Analysis",
                "Analyze source code for bugs, weaknesses and improvement opportunities.",
                100
        );

        add(
                "coding",
                "Code Generation",
                "Generate implementation code for new capabilities.",
                100
        );

        add(
                "coding",
                "Code Testing",
                "Create and run automated tests for generated improvements.",
                100
        );

        add(
                "coding",
                "Safe Code Evolution",
                "Test proposed code changes in an isolated environment before adoption.",
                100
        );

        add(
                "coding",
                "Automatic Bug Fixing",
                "Detect failing tests, identify likely causes and generate corrective changes.",
                100
        );

        add(
                "memory",
                "Long-Term Memory",
                "Improve persistent memory and retrieval of important information.",
                90
        );

        add(
                "memory",
                "Memory Relevance",
                "Rank memories by relevance instead of loading unrelated information.",
                90
        );

        add(
                "memory",
                "Memory Consolidation",
                "Combine related memories and remove redundant information.",
                85
        );

        add(
                "knowledge",
                "Knowledge Deduplication",
                "Detect and merge duplicate or substantially identical knowledge.",
                85
        );

        add(
                "knowledge",
                "Knowledge Confidence",
                "Track confidence and supporting sources for stored knowledge.",
                95
        );

        add(
                "knowledge",
                "Knowledge Updating",
                "Detect outdated knowledge and search for newer information.",
                100
        );

        add(
                "system",
                "Capability Discovery",
                "Detect missing capabilities and create improvement tasks automatically.",
                100
        );

        add(
                "system",
                "Automatic Improvement Planning",
                "Choose improvement targets using benchmark results, priorities and capability gaps.",
                100
        );

        add(
                "system",
                "Improvement Verification",
                "Keep an improvement only when measurable tests show that it works.",
                100
        );

        add(
                "system",
                "Regression Detection",
                "Detect when a new improvement breaks an existing capability.",
                100
        );
    }

    private void add(
            String domain,
            String name,
            String description,
            int priority
    ) {

        features.add(
                new Feature(
                        domain,
                        name,
                        description,
                        priority,
                        false,
                        0,
                        0
                )
        );
    }

    public synchronized List<Feature> getAll() {

        return List.copyOf(
                features
        );
    }

    public synchronized List<Feature> getByDomain(
            String domain
    ) {

        if (domain == null
                || domain.isBlank()) {

            return List.of();
        }

        String normalized =
                domain.trim()
                        .toLowerCase();

        return features.stream()
                .filter(
                        feature ->
                                feature.domain()
                                        .equalsIgnoreCase(
                                                normalized
                                        )
                )
                .toList();
    }

    public synchronized Feature getHighestPriority(
            String domain
    ) {

        return getByDomain(domain)
                .stream()
                .filter(
                        feature ->
                                !feature.completed()
                )
                .max(
                        java.util.Comparator
                                .comparingInt(
                                        Feature::priority
                                )
                )
                .orElse(null);
    }

    public synchronized Feature find(
            String name
    ) {

        if (name == null
                || name.isBlank()) {

            return null;
        }

        return features.stream()
                .filter(
                        feature ->
                                feature.name()
                                        .equalsIgnoreCase(
                                                name.trim()
                                        )
                )
                .findFirst()
                .orElse(null);
    }

    public synchronized void markStarted(
            String name
    ) {

        Feature feature =
                find(name);

        if (feature == null) {
            return;
        }

        int index =
                features.indexOf(feature);

        if (index < 0) {
            return;
        }

        features.set(
                index,
                new Feature(
                        feature.domain(),
                        feature.name(),
                        feature.description(),
                        feature.priority(),
                        true,
                        feature.attempts() + 1,
                        feature.successes()
                )
        );
    }

    public synchronized void markSuccessful(
            String name
    ) {

        Feature feature =
                find(name);

        if (feature == null) {
            return;
        }

        int index =
                features.indexOf(feature);

        if (index < 0) {
            return;
        }

        features.set(
                index,
                new Feature(
                        feature.domain(),
                        feature.name(),
                        feature.description(),
                        feature.priority(),
                        false,
                        feature.attempts(),
                        feature.successes() + 1
                )
        );
    }

    public synchronized void resetProgress() {

        for (int i = 0;
             i < features.size();
             i++) {

            Feature feature =
                    features.get(i);

            features.set(
                    i,
                    new Feature(
                            feature.domain(),
                            feature.name(),
                            feature.description(),
                            feature.priority(),
                            false,
                            0,
                            0
                    )
            );
        }
    }

    public synchronized int totalFeatures() {

        return features.size();
    }

    public synchronized int completedFeatures() {

        return (int) features.stream()
                .filter(
                        Feature::completed
                )
                .count();
    }

    public synchronized int successfulAttempts() {

        return features.stream()
                .mapToInt(
                        Feature::successes
                )
                .sum();
    }

    public synchronized List<Feature> getHighestPriorityFeatures(
            int limit
    ) {

        if (limit <= 0) {
            return List.of();
        }

        return features.stream()
                .filter(
                        feature ->
                                !feature.completed()
                )
                .sorted(
                        java.util.Comparator
                                .comparingInt(
                                        Feature::priority
                                )
                                .reversed()
                )
                .limit(limit)
                .toList();
    }

    public record Feature(
            String domain,
            String name,
            String description,
            int priority,
            boolean completed,
            int attempts,
            int successes
    ) {
    }
}
