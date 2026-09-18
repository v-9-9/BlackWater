package service;

import java.time.Instant;

public class KnowledgeEntry {

    private final String information;
    private final String source;
    private final Instant learnedAt;

    public KnowledgeEntry(
            String information,
            String source
    ) {
        this.information = information;
        this.source = source;
        this.learnedAt = Instant.now();
    }

    public String getInformation() {
        return information;
    }

    public String getSource() {
        return source;
    }

    public Instant getLearnedAt() {
        return learnedAt;
    }
}
