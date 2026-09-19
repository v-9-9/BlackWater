package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LearningEngine {

    private final WebKnowledgeCollector webKnowledgeCollector;
    private final ResearchEngine researchEngine;
    private final KnowledgeEvaluator knowledgeEvaluator;
    private final KnowledgeService knowledgeService;

    public LearningEngine(
            WebKnowledgeCollector webKnowledgeCollector,
            ResearchEngine researchEngine,
            KnowledgeEvaluator knowledgeEvaluator,
            KnowledgeService knowledgeService
    ) {
        this.webKnowledgeCollector =
                webKnowledgeCollector;

        this.researchEngine =
                researchEngine;

        this.knowledgeEvaluator =
                knowledgeEvaluator;

        this.knowledgeService =
                knowledgeService;
    }

    public synchronized List<KnowledgeEntry> learn(
            String topic
    ) {

        if (topic == null
                || topic.isBlank()) {

            return List.of();
        }

        ResearchEngine.ResearchResult research =
                researchEngine.research(
                        topic
                );

        List<KnowledgeEntry> learned =
                new ArrayList<>();

        for (
                ResearchEngine.SourceResult source :
                research.sources()
        ) {

            if (source == null
                    || source.content() == null
                    || source.content().isBlank()) {
                continue;
            }

            KnowledgeEntry entry =
                    createKnowledgeEntry(
                            topic,
                            source
                    );

            if (entry == null) {
                continue;
            }

            if (
                    knowledgeEvaluator.isUseful(
                            entry
                    )
            ) {

                KnowledgeEntry saved =
                        knowledgeService.learn(
                                entry
                        );

                if (saved != null) {
                    learned.add(
                            saved
                    );
                }
            }
        }

        return learned;
    }

    public synchronized List<KnowledgeEntry> learnMultiple(
            List<String> topics
    ) {

        if (topics == null
                || topics.isEmpty()) {

            return List.of();
        }

        List<KnowledgeEntry> result =
                new ArrayList<>();

        for (String topic :
                topics) {

            if (topic == null
                    || topic.isBlank()) {
                continue;
            }

            try {

                result.addAll(
                        learn(
                                topic
                        )
                );

            } catch (Exception ignored) {
            }
        }

        return result;
    }

    public synchronized ResearchEngine.ResearchResult research(
            String question
    ) {

        return researchEngine.research(
                question
        );
    }

    public synchronized List<KnowledgeEntry> recentKnowledge(
            String query
    ) {

        return knowledgeService.search(
                query
        );
    }

    public synchronized int knowledgeCount() {

        return knowledgeService.count();
    }

    public synchronized List<KnowledgeEntry> getKnowledge(
            String query
    ) {

        return knowledgeService.search(
                query
        );
    }

    private KnowledgeEntry createKnowledgeEntry(
            String topic,
            ResearchEngine.SourceResult source
    ) {

        try {

            String content =
                    cleanContent(
                            source.content()
                    );

            if (content.isBlank()) {
                return null;
            }

            return new KnowledgeEntry(
                    topic.trim(),
                    source.title(),
                    content,
                    source.url(),
                    source.source(),
                    source.confidence()
            );

        } catch (Exception ignored) {

            return null;
        }
    }

    private String cleanContent(
            String content
    ) {

        if (content == null) {
            return "";
        }

        return content
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }
}
