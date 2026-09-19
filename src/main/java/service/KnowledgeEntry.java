package service;

import java.time.Instant;

public record KnowledgeEntry(
        String topic,
        String title,
        String content,
        String sourceUrl,
        String source,
        double confidence,
        Instant learnedAt
) {

    public KnowledgeEntry(
            String topic,
            String title,
            String content,
            String sourceUrl,
            String source,
            double confidence
    ) {
        this(
                topic,
                title,
                content,
                sourceUrl,
                source,
                confidence,
                Instant.now()
        );
    }

    public KnowledgeEntry(
            String topic,
            String title,
            String content,
            String sourceUrl,
            String source
    ) {
        this(
                topic,
                title,
                content,
                sourceUrl,
                source,
                0.50,
                Instant.now()
        );
    }

    public boolean isReliable() {
        return confidence >= 0.70;
    }

    public String searchableText() {
        return (
                safe(topic)
                        + " "
                        + safe(title)
                        + " "
                        + safe(content)
                        + " "
                        + safe(source)
        ).trim();
    }

    private static String safe(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }
}
