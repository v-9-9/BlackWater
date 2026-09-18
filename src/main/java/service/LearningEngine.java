package service;

import org.springframework.stereotype.Service;

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

    public String learn(String topic) {

        if (topic == null || topic.isBlank()) {
            return "Learning topic is empty.";
        }

        String information = collector.collect(topic);

        if (!evaluator.isUseful(information)) {
            return "No useful information was found.";
        }

        knowledgeService.learn(
                "Topic: " + topic +
                " | Source: Wikipedia" +
                " | Information: " + information
        );

        return "Blackwater learned new information about: " + topic;
    }
}
