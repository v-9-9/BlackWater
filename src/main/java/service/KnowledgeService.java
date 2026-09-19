package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private static final Path STORAGE =
            Path.of("blackwater-knowledge.txt");

    private static final int MAX_ENTRIES = 10000;
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_CONTENT_LENGTH = 12000;

    private final List<KnowledgeEntry> entries =
            new CopyOnWriteArrayList<>();

    public KnowledgeService() {
        load();
    }

    public synchronized KnowledgeEntry learn(KnowledgeEntry entry) {
        if (entry == null) {
            return null;
        }

        KnowledgeEntry normalized = normalize(entry);

        String fingerprint = fingerprint(normalized);

        for (KnowledgeEntry existing : entries) {
            if (fingerprint(existing).equals(fingerprint)) {
                if (normalized.confidence() > existing.confidence()) {
                    entries.remove(existing);
                    entries.add(normalized);
                    save();
                    return normalized;
                }

                return existing;
            }
        }

        if (entries.size() >= MAX_ENTRIES) {
            removeWeakest();
        }

        entries.add(normalized);
        save();

        return normalized;
    }

    public KnowledgeEntry learn(
            String topic,
            String title,
            String content,
            String sourceUrl,
            String source,
            double confidence
    ) {
        return learn(new KnowledgeEntry(
                topic,
                title,
                content,
                sourceUrl,
                source,
                confidence
        ));
    }

    public List<KnowledgeEntry> search(String query) {
        String normalizedQuery = normalizeText(query);

        if (normalizedQuery.isBlank()) {
            return recent(MAX_SEARCH_RESULTS);
        }

        String[] tokens = normalizedQuery
                .split("\\s+");

        return entries.stream()
                .map(entry -> new ScoredEntry(
                        entry,
                        score(entry, tokens)
                ))
                .filter(item -> item.score > 0)
                .sorted(
                        Comparator
                                .comparingDouble(ScoredEntry::score)
                                .reversed()
                                .thenComparing(
                                        item -> item.entry.learnedAt(),
                                        Comparator.reverseOrder()
                                )
                )
                .limit(MAX_SEARCH_RESULTS)
                .map(ScoredEntry::entry)
                .collect(Collectors.toList());
    }

    public List<KnowledgeEntry> getKnowledge() {
        return List.copyOf(entries);
    }

    public List<KnowledgeEntry> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_SEARCH_RESULTS));

        return entries.stream()
                .sorted(
                        Comparator.comparing(
                                KnowledgeEntry::learnedAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    public int count() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    public synchronized boolean forget(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }

        boolean removed = entries.removeIf(entry ->
                sourceUrl.equalsIgnoreCase(
                        safe(entry.sourceUrl())
                )
        );

        if (removed) {
            save();
        }

        return removed;
    }

    public double averageConfidence() {
        if (entries.isEmpty()) {
            return 0.0;
        }

        return entries.stream()
                .mapToDouble(KnowledgeEntry::confidence)
                .average()
                .orElse(0.0);
    }

    public List<KnowledgeEntry> reliableKnowledge() {
        return entries.stream()
                .filter(KnowledgeEntry::isReliable)
                .sorted(
                        Comparator.comparingDouble(
                                KnowledgeEntry::confidence
                        ).reversed()
                )
                .collect(Collectors.toList());
    }

    private double score(
            KnowledgeEntry entry,
            String[] tokens
    ) {
        String searchable =
                entry.searchableText()
                        .toLowerCase(Locale.ROOT);

        double score = 0.0;

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            if (searchable.contains(token)) {
                score += 1.0;
            }

            if (safe(entry.title())
                    .toLowerCase(Locale.ROOT)
                    .contains(token)) {
                score += 2.0;
            }

            if (safe(entry.topic())
                    .toLowerCase(Locale.ROOT)
                    .contains(token)) {
                score += 2.0;
            }
        }

        score += entry.confidence() * 2.0;

        if (entry.isReliable()) {
            score += 1.0;
        }

        return score;
    }

    private KnowledgeEntry normalize(
            KnowledgeEntry entry
    ) {
        String topic = clean(entry.topic(), 500);
        String title = clean(entry.title(), 1000);
        String content = clean(
                entry.content(),
                MAX_CONTENT_LENGTH
        );
        String sourceUrl = clean(entry.sourceUrl(), 4000);
        String source = clean(entry.source(), 500);

        double confidence =
                Math.max(
                        0.0,
                        Math.min(1.0, entry.confidence())
                );

        Instant learnedAt =
                entry.learnedAt() == null
                        ? Instant.now()
                        : entry.learnedAt();

        return new KnowledgeEntry(
                topic,
                title,
                content,
                sourceUrl,
                source,
                confidence,
                learnedAt
        );
    }

    private String fingerprint(
            KnowledgeEntry entry
    ) {
        String content =
                normalizeText(entry.content());

        String url =
                normalizeText(entry.sourceUrl());

        if (!url.isBlank()) {
            return "url:" + url;
        }

        return "content:" + content;
    }

    private void removeWeakest() {
        entries.stream()
                .min(
                        Comparator
                                .comparingDouble(
                                        KnowledgeEntry::confidence
                                )
                                .thenComparing(
                                        KnowledgeEntry::learnedAt,
                                        Comparator.nullsFirst(
                                                Comparator.naturalOrder()
                                        )
                                )
                )
                .ifPresent(entries::remove);
    }

    private synchronized void save() {
        try {
            Path parent = STORAGE.toAbsolutePath().getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            List<String> lines = new ArrayList<>();

            for (KnowledgeEntry entry : entries) {
                lines.add(serialize(entry));
            }

            Files.write(
                    STORAGE,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

        } catch (IOException ignored) {
        }
    }

    private void load() {
        if (!Files.exists(STORAGE)) {
            return;
        }

        try {
            List<String> lines =
                    Files.readAllLines(
                            STORAGE,
                            StandardCharsets.UTF_8
                    );

            for (String line : lines) {
                KnowledgeEntry entry =
                        deserialize(line);

                if (entry != null) {
                    entries.add(entry);
                }
            }

            if (entries.size() > MAX_ENTRIES) {
                while (entries.size() > MAX_ENTRIES) {
                    removeWeakest();
                }
            }

        } catch (IOException ignored) {
        }
    }

    private String serialize(
            KnowledgeEntry entry
    ) {
        return String.join(
                "|",
                encode(entry.topic()),
                encode(entry.title()),
                encode(entry.content()),
                encode(entry.sourceUrl()),
                encode(entry.source()),
                Double.toString(entry.confidence()),
                encode(
                        entry.learnedAt() == null
                                ? ""
                                : entry.learnedAt().toString()
                )
        );
    }

    private KnowledgeEntry deserialize(
            String line
    ) {
        if (line == null || line.isBlank()) {
            return null;
        }

        try {
            String[] parts = line.split(
                    "\\|",
                    -1
            );

            if (parts.length >= 7) {
                Instant learnedAt =
                        parseInstant(
                                decode(parts[6])
                        );

                return normalize(
                        new KnowledgeEntry(
                                decode(parts[0]),
                                decode(parts[1]),
                                decode(parts[2]),
                                decode(parts[3]),
                                decode(parts[4]),
                                Double.parseDouble(parts[5]),
                                learnedAt
                        )
                );
            }

            /*
             * Compatibility with the older
             * five-field knowledge format.
             */
            if (parts.length >= 5) {
                return normalize(
                        new KnowledgeEntry(
                                decode(parts[0]),
                                decode(parts[1]),
                                decode(parts[2]),
                                decode(parts[3]),
                                decode(parts[4]),
                                0.50,
                                Instant.now()
                        )
                );
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String encode(String value) {
        return Base64.getEncoder()
                .encodeToString(
                        safe(value)
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );
    }

    private String decode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            return new String(
                    Base64.getDecoder().decode(value),
                    StandardCharsets.UTF_8
            );
        } catch (Exception ignored) {
            return value;
        }
    }

    private String clean(
            String value,
            int maxLength
    ) {
        String result = safe(value).trim();

        if (result.length() <= maxLength) {
            return result;
        }

        return result.substring(0, maxLength);
    }

    private String normalizeText(String value) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredEntry(
            KnowledgeEntry entry,
            double score
    ) {
    }
}
