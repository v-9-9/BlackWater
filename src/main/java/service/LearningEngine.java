package service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LearningEngine {

    private final WebKnowledgeCollector collector;
    private final KnowledgeEvaluator evaluator;
    private final KnowledgeService knowledgeService;

    public LearningEngine(
            WebKnowledgeCollector collector,
            KnowledgeEvaluator evaluator,
            KnowledgeService knowledgeService
    ) {
        this.collector = collector;
        this.evaluator = evaluator;
        this.knowledgeService = knowledgeService;
    }

    public synchronized String learn(
            String topic
    ) {

        if (topic == null
                || topic.isBlank()) {

            return "Learning topic is empty.";
        }

        String cleanTopic =
                topic.trim();

        /*
         * Step 1:
         * Search the web.
         */

        String information =
                collector.collect(
                        cleanTopic
                );

        if (information == null
                || information.isBlank()) {

            return
                    "No information was found for: "
                            + cleanTopic;
        }

        /*
         * Step 2:
         * Evaluate the collected information.
         */

        if (!evaluator.isUseful(
                information
        )) {

            return
                    "The collected information "
                            + "was not useful enough to learn.";
        }

        /*
         * Step 3:
         * Store the new knowledge.
         */

        knowledgeService.learn(
                information,
                "Web Research"
        );

        /*
         * Step 4:
         * Check how much knowledge now exists.
         */

        long knowledgeCount =
                knowledgeService.count();

        return
                "Blackwater learned about: "
                        + cleanTopic
                        + System.lineSeparator()
                        + "Knowledge entries: "
                        + knowledgeCount
                        + System.lineSeparator()
                        + "Learned at: "
                        + Instant.now();
    }

    public synchronized String learnMultiple(
            List<String> topics
    ) {

        if (topics == null
                || topics.isEmpty()) {

            return "No learning topics provided.";
        }

        int learned = 0;

        StringBuilder report =
                new StringBuilder();

        for (String topic : topics) {

            if (topic == null
                    || topic.isBlank()) {

                continue;
            }

            String result =
                    learn(topic);

            if (!result.startsWith(
                    "No information"
            )
                    && !result.startsWith(
                            "The collected information"
                    )
                    && !result.startsWith(
                            "Learning topic"
                    )) {

                learned++;
            }

            report.append(
                    result
            )
            .append(
                    System.lineSeparator()
            )
            .append(
                    System.lineSeparator()
            );
        }

        report.append(
                "Learning cycle complete."
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Topics processed: "
        )
        .append(
                topics.size()
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Successful learning attempts: "
        )
        .append(
                learned
        );

        return report.toString().trim();
    }

    public synchronized List<String> recentKnowledge(
            int limit
    ) {

        return knowledgeService.getRecent(
                limit
        );
    }

    public synchronized long knowledgeCount() {

        return knowledgeService.count();
    }
}
