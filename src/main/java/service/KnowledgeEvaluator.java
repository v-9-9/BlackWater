package service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;

@Service
public class KnowledgeEvaluator {

    private static final int MIN_CONTENT_LENGTH = 40;
    private static final int MAX_CONTENT_LENGTH = 12000;

    public boolean isUseful(KnowledgeEntry entry) {
        if (entry == null) {
            return false;
        }

        String content = safe(entry.content()).trim();

        if (content.length() < MIN_CONTENT_LENGTH) {
            return false;
        }

        if (content.length() > MAX_CONTENT_LENGTH) {
            return false;
        }

        if (entry.topic() == null || entry.topic().isBlank()) {
            return false;
        }

        if (isLowQualityContent(content)) {
            return false;
        }

        return calculateQuality(entry) >= 0.35;
    }

    public double evaluate(KnowledgeEntry entry) {
        if (entry == null) {
            return 0.0;
        }

        return calculateQuality(entry);
    }

    public double evaluate(
            String content,
            String source,
            String sourceUrl
    ) {
        KnowledgeEntry entry = new KnowledgeEntry(
                "",
                "",
                content,
                sourceUrl,
                source,
                0.50
        );

        return calculateQuality(entry);
    }

    private double calculateQuality(KnowledgeEntry entry) {
        String content = safe(entry.content()).trim();

        if (content.length() < MIN_CONTENT_LENGTH) {
            return 0.0;
        }

        double score = 0.25;

        score += contentQuality(content);
        score += sourceQuality(entry.source());
        score += urlQuality(entry.sourceUrl());
        score += structureQuality(content);
        score += entry.confidence() * 0.25;

        return clamp(score);
    }

    private double contentQuality(String content) {
        double score = 0.0;

        if (content.length() >= 200) {
            score += 0.10;
        }

        if (content.length() >= 500) {
            score += 0.05;
        }

        if (content.contains(".")) {
            score += 0.05;
        }

        if (containsUsefulStructure(content)) {
            score += 0.05;
        }

        return score;
    }

    private double sourceQuality(String source) {
        String value =
                safe(source)
                        .toLowerCase(Locale.ROOT);

        if (value.isBlank()) {
            return 0.0;
        }

        if (value.contains("official")) {
            return 0.15;
        }

        if (value.contains("documentation")
                || value.contains("docs")) {
            return 0.14;
        }

        if (value.contains("github")) {
            return 0.12;
        }

        if (value.contains("stackoverflow")) {
            return 0.10;
        }

        if (value.contains("wikipedia")) {
            return 0.10;
        }

        if (value.contains("arxiv")) {
            return 0.13;
        }

        if (value.contains("reddit")) {
            return 0.06;
        }

        if (value.contains("youtube")) {
            return 0.05;
        }

        if (value.contains("tiktok")
                || value.contains("instagram")) {
            return 0.025;
        }

        return 0.04;
    }

    private double urlQuality(String sourceUrl) {
        String url = safe(sourceUrl).trim();

        if (url.isBlank()) {
            return 0.0;
        }

        try {
            URI uri = URI.create(url);

            String host =
                    uri.getHost() == null
                            ? ""
                            : uri.getHost()
                            .toLowerCase(Locale.ROOT);

            if (host.endsWith(".gov")
                    || host.endsWith(".edu")) {
                return 0.12;
            }

            if (host.contains("github.com")
                    || host.contains("developer.mozilla.org")
                    || host.contains("docs.spring.io")
                    || host.contains("docs.oracle.com")) {
                return 0.12;
            }

            if (host.contains("wikipedia.org")
                    || host.contains("arxiv.org")) {
                return 0.10;
            }

            if (host.contains("stackoverflow.com")) {
                return 0.08;
            }

            return 0.04;

        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double structureQuality(String content) {
        String lower =
                content.toLowerCase(Locale.ROOT);

        int signals = 0;

        if (lower.contains("because")) {
            signals++;
        }

        if (lower.contains("therefore")) {
            signals++;
        }

        if (lower.contains("example")) {
            signals++;
        }

        if (lower.contains("according")) {
            signals++;
        }

        if (lower.contains("source")) {
            signals++;
        }

        if (lower.contains("http")) {
            signals++;
        }

        return Math.min(signals * 0.01, 0.05);
    }

    private boolean containsUsefulStructure(
            String content
    ) {
        return content.contains(". ")
                || content.contains("\n")
                || content.contains(": ")
                || content.contains("- ");
    }

    private boolean isLowQualityContent(
            String content
    ) {
        String lower =
                content.toLowerCase(Locale.ROOT);

        if (lower.contains("enable javascript")
                && content.length() < 500) {
            return true;
        }

        if (lower.contains("captcha")
                && content.length() < 500) {
            return true;
        }

        if (lower.contains("access denied")
                && content.length() < 500) {
            return true;
        }

        if (lower.contains("robot check")
                && content.length() < 500) {
            return true;
        }

        if (lower.matches(
                "^(error|not found|forbidden|unauthorized).*"
        )) {
            return true;
        }

        return false;
    }

    private double clamp(double value) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
