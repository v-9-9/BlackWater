package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeService {

    private static final Path KNOWLEDGE_FILE =
            Path.of("blackwater-knowledge.txt");

    public synchronized void learn(String information) {

        if (information == null || information.isBlank()) {
            return;
        }

        String cleaned = information
                .trim()
                .replaceAll("\\s+", " ");

        try {
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

        String[] words = query
                .toLowerCase()
                .split("\\s+");

        List<String> results = new ArrayList<>();

        try {

            if (!Files.exists(KNOWLEDGE_FILE)) {
                return results;
            }

            for (String line : Files.readAllLines(KNOWLEDGE_FILE)) {

                String lowerLine = line.toLowerCase();

                int matches = 0;

                for (String word : words) {
                    if (word.length() > 2 && lowerLine.contains(word)) {
                        matches++;
                    }
                }

                if (matches > 0) {
                    results.add(line);
                }

                if (results.size() >= 20) {
                    break;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not read knowledge.",
                    e
            );
        }

        return results;
    }

    public synchronized long count() {

        try {

            if (!Files.exists(KNOWLEDGE_FILE)) {
                return 0;
            }

            try (var lines = Files.lines(KNOWLEDGE_FILE)) {
                return lines.count();
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not count knowledge.",
                    e
            );
        }
    }
}
