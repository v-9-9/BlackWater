package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeService {

    private static final Path KNOWLEDGE_FILE =
            Path.of("blackwater-knowledge.txt");

    private static final int MAX_RESULTS = 20;

    public synchronized void learn(String information) {

        if (information == null || information.isBlank()) {
            return;
        }

        String cleaned = clean(information);

        if (cleaned.isBlank()) {
            return;
        }

        try {

            Set<String> existing =
                    new LinkedHashSet<>();

            if (Files.exists(KNOWLEDGE_FILE)) {
                existing.addAll(
                        Files.readAllLines(KNOWLEDGE_FILE)
                );
            }

            if (existing.contains(cleaned)) {
                return;
            }

            Files.writeString(
                    KNOWLEDGE_FILE,
                    cleaned + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not save knowledge.",
                    e
            );
        }
    }

    public synchronized List<String> search(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {

            if (!Files.exists(KNOWLEDGE_FILE)) {
                return List.of();
            }

            String[] words = query
                    .toLowerCase()
                    .trim()
                    .split("\\s+");

            List<KnowledgeMatch> matches =
                    new ArrayList<>();

            Set<String> seen =
                    new LinkedHashSet<>();

            for (String line :
                    Files.readAllLines(KNOWLEDGE_FILE)) {

                if (line == null || line.isBlank()) {
                    continue;
                }

                String cleaned = clean(line);

                if (cleaned.isBlank()
                        || !seen.add(cleaned)) {
                    continue;
                }

                String lower =
                        cleaned.toLowerCase();

                int score = 0;

                for (String word : words) {

                    if (word.length() < 3) {
                        continue;
                    }

                    if (lower.contains(word)) {
                        score++;
                    }
                }

                if (score > 0) {
                    matches.add(
                            new KnowledgeMatch(
                                    cleaned,
                                    score
                            )
                    );
                }
            }

            matches.sort(
                    Comparator.comparingInt(
                            KnowledgeMatch::score
                    ).reversed()
            );

            return matches.stream()
                    .limit(MAX_RESULTS)
                    .map(KnowledgeMatch::information)
                    .toList();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not read knowledge.",
                    e
            );
        }
    }

    public synchronized long count() {

        try {

            if (!Files.exists(KNOWLEDGE_FILE)) {
                return 0;
            }

            try (var lines =
                         Files.lines(KNOWLEDGE_FILE)) {

                return lines
                        .filter(line ->
                                line != null
                                && !line.isBlank())
                        .count();
            }

        } catch (IOException e) {

            throw new RuntimeException(
                    "Could not count knowledge.",
                    e
            );
        }
    }

    private String clean(String text) {

        return text
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record KnowledgeMatch(
            String information,
            int score
    ) {
    }
}
