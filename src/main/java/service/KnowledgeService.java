package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeService {

    private static final Path KNOWLEDGE_FILE =
            Path.of("blackwater-knowledge.txt");

    private static final int MAX_RESULTS = 20;

    private static final int MAX_ENTRY_LENGTH = 12000;

    public synchronized void learn(
            String information
    ) {

        learn(
                information,
                "internal"
        );
    }

    public synchronized void learn(
            String information,
            String source
    ) {

        if (information == null
                || information.isBlank()) {

            return;
        }

        String cleaned =
                clean(information);

        if (cleaned.isBlank()) {
            return;
        }

        if (cleaned.length()
                > MAX_ENTRY_LENGTH) {

            cleaned =
                    cleaned.substring(
                            0,
                            MAX_ENTRY_LENGTH
                    ).trim();
        }

        String cleanSource =
                source == null
                        || source.isBlank()
                        ? "unknown"
                        : source.trim();

        try {

            ensureFile();

            List<KnowledgeRecord> records =
                    loadRecords();

            String fingerprint =
                    fingerprint(cleaned);

            for (KnowledgeRecord record : records) {

                if (record.fingerprint()
                        .equals(fingerprint)) {

                    return;
                }
            }

            KnowledgeRecord record =
                    new KnowledgeRecord(
                            cleaned,
                            cleanSource,
                            Instant.now().toString(),
                            fingerprint
                    );

            records.add(record);

            saveRecords(records);

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not save knowledge.",
                    e
            );
        }
    }

    public synchronized List<String> search(
            String query
    ) {

        if (query == null
                || query.isBlank()) {

            return List.of();
        }

        try {

            ensureFile();

            List<KnowledgeRecord> records =
                    loadRecords();

            if (records.isEmpty()) {
                return List.of();
            }

            String[] words =
                    tokenize(query);

            List<KnowledgeMatch> matches =
                    new ArrayList<>();

            for (KnowledgeRecord record :
                    records) {

                int score =
                        calculateScore(
                                record,
                                words
                        );

                if (score > 0) {

                    matches.add(
                            new KnowledgeMatch(
                                    record,
                                    score
                            )
                    );
                }
            }

            matches.sort(
                    Comparator
                            .comparingInt(
                                    KnowledgeMatch::score
                            )
                            .reversed()
                            .thenComparing(
                                    match ->
                                            match.record()
                                                    .learnedAt(),
                                    Comparator.reverseOrder()
                            )
            );

            return matches.stream()
                    .limit(MAX_RESULTS)
                    .map(
                            match ->
                                    formatForContext(
                                            match.record()
                                    )
                    )
                    .toList();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not search knowledge.",
                    e
            );
        }
    }

    public synchronized long count() {

        try {

            ensureFile();

            return loadRecords()
                    .size();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not count knowledge.",
                    e
            );
        }
    }

    public synchronized void clear() {

        try {

            if (Files.exists(
                    KNOWLEDGE_FILE
            )) {

                Files.delete(
                        KNOWLEDGE_FILE
                );
            }

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not clear knowledge.",
                    e
            );
        }
    }

    public synchronized List<String> getRecent(
            int limit
    ) {

        if (limit <= 0) {
            return List.of();
        }

        try {

            ensureFile();

            List<KnowledgeRecord> records =
                    loadRecords();

            return records.stream()
                    .sorted(
                            Comparator.comparing(
                                    KnowledgeRecord::learnedAt
                            ).reversed()
                    )
                    .limit(limit)
                    .map(
                            this::formatForContext
                    )
                    .toList();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not load recent knowledge.",
                    e
            );
        }
    }

    private int calculateScore(
            KnowledgeRecord record,
            String[] words
    ) {

        String information =
                record.information()
                        .toLowerCase();

        String source =
                record.source()
                        .toLowerCase();

        int score = 0;

        for (String word : words) {

            if (word.length() < 2) {
                continue;
            }

            if (information.contains(word)) {
                score += 3;
            }

            if (source.contains(word)) {
                score += 1;
            }
        }

        /*
         * Exact phrase match receives a stronger score.
         */

        return score;
    }

    private String[] tokenize(
            String query
    ) {

        return query
                .toLowerCase()
                .replaceAll(
                        "[^\\p{L}\\p{N}]+",
                        " "
                )
                .trim()
                .split("\\s+");
    }

    private String formatForContext(
            KnowledgeRecord record
    ) {

        return
                "Source: "
                        + record.source()
                        + System.lineSeparator()
                        + "Learned: "
                        + record.learnedAt()
                        + System.lineSeparator()
                        + "Information: "
                        + record.information();
    }

    private List<KnowledgeRecord> loadRecords()
            throws IOException {

        if (!Files.exists(
                KNOWLEDGE_FILE
        )) {

            return new ArrayList<>();
        }

        List<String> lines =
                Files.readAllLines(
                        KNOWLEDGE_FILE,
                        StandardCharsets.UTF_8
                );

        List<KnowledgeRecord> records =
                new ArrayList<>();

        for (String line : lines) {

            if (line == null
                    || line.isBlank()) {

                continue;
            }

            KnowledgeRecord record =
                    parseRecord(line);

            if (record != null) {

                records.add(record);
            }
        }

        /*
         * Remove duplicates from older data too.
         */

        Map<String, KnowledgeRecord> unique =
                new LinkedHashMap<>();

        for (KnowledgeRecord record :
                records) {

            unique.putIfAbsent(
                    record.fingerprint(),
                    record
            );
        }

        return new ArrayList<>(
                unique.values()
        );
    }

    private KnowledgeRecord parseRecord(
            String line
    ) {

        try {

            String[] parts =
                    line.split(
                            "\\|",
                            4
                    );

            if (parts.length < 4) {

                /*
                 * Compatibility with the old format.
                 */

                String oldInformation =
                        clean(line);

                if (oldInformation.isBlank()) {
                    return null;
                }

                return new KnowledgeRecord(
                        oldInformation,
                        "legacy",
                        Instant.EPOCH.toString(),
                        fingerprint(
                                oldInformation
                        )
                );
            }

            String source =
                    parts[0].trim();

            String learnedAt =
                    parts[1].trim();

            String fingerprint =
                    parts[2].trim();

            String information =
                    clean(
                            parts[3]
                    );

            if (information.isBlank()) {
                return null;
            }

            if (fingerprint.isBlank()) {

                fingerprint =
                        fingerprint(
                                information
                        );
            }

            return new KnowledgeRecord(
                    information,
                    source.isBlank()
                            ? "unknown"
                            : source,
                    learnedAt.isBlank()
                            ? Instant.EPOCH.toString()
                            : learnedAt,
                    fingerprint
            );

        } catch (Exception ignored) {

            return null;
        }
    }

    private void saveRecords(
            List<KnowledgeRecord> records
    )
            throws IOException {

        List<String> lines =
                new ArrayList<>();

        for (KnowledgeRecord record :
                records) {

            lines.add(
                    record.source()
                            + "|"
                            + record.learnedAt()
                            + "|"
                            + record.fingerprint()
                            + "|"
                            + record.information()
            );
        }

        Files.write(
                KNOWLEDGE_FILE,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private void ensureFile()
            throws IOException {

        Path parent =
                KNOWLEDGE_FILE.getParent();

        if (parent != null) {

            Files.createDirectories(
                    parent
            );
        }

        if (!Files.exists(
                KNOWLEDGE_FILE
        )) {

            Files.createFile(
                    KNOWLEDGE_FILE
            );
        }
    }

    private String clean(
            String text
    ) {

        return text
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                )
                .replace(
                        "|",
                        "/"
                );
    }

    private String fingerprint(
            String text
    ) {

        String normalized =
                text
                        .toLowerCase()
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        return Integer.toHexString(
                normalized.hashCode()
        );
    }

    private record KnowledgeRecord(
            String information,
            String source,
            String learnedAt,
            String fingerprint
    ) {
    }

    private record KnowledgeMatch(
            KnowledgeRecord record,
            int score
    ) {
    }
}
