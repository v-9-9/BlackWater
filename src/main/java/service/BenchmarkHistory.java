package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class BenchmarkHistory {

    private static final Path HISTORY_FILE =
            Path.of(
                    "blackwater-benchmark-history.txt"
            );

    public synchronized void record(
            BenchmarkEngine.BenchmarkSummary summary
    ) {

        if (summary == null) {
            return;
        }

        String line =
                Instant.now()
                        + "|"
                        + summary.averageScore()
                        + "|"
                        + summary.knowledge().score()
                        + "|"
                        + summary.reasoning().score()
                        + "|"
                        + summary.research().score()
                        + "|"
                        + summary.coding().score()
                        + "|"
                        + summary.memory().score()
                        + System.lineSeparator();

        try {

            Files.writeString(
                    HISTORY_FILE,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException ignored) {
        }
    }

    public synchronized List<HistoryEntry>
    getHistory() {

        if (!Files.exists(HISTORY_FILE)) {
            return List.of();
        }

        List<HistoryEntry> result =
                new ArrayList<>();

        try {

            List<String> lines =
                    Files.readAllLines(
                            HISTORY_FILE,
                            StandardCharsets.UTF_8
                    );

            for (String line : lines) {

                HistoryEntry entry =
                        parse(line);

                if (entry != null) {
                    result.add(entry);
                }
            }

        } catch (IOException ignored) {
        }

        return result;
    }

    public synchronized HistoryEntry
    getLatest() {

        List<HistoryEntry> history =
                getHistory();

        if (history.isEmpty()) {
            return null;
        }

        return history.get(
                history.size() - 1
        );
    }

    public synchronized int
    getBestAverage() {

        return getHistory()
                .stream()
                .mapToInt(
                        HistoryEntry::averageScore
                )
                .max()
                .orElse(0);
    }

    public synchronized int
    getPreviousAverage() {

        List<HistoryEntry> history =
                getHistory();

        if (history.size() < 2) {
            return 0;
        }

        return history.get(
                history.size() - 2
        ).averageScore();
    }

    public synchronized int
    getImprovementFromPrevious() {

        HistoryEntry latest =
                getLatest();

        if (latest == null) {
            return 0;
        }

        return latest.averageScore()
                - getPreviousAverage();
    }

    private HistoryEntry parse(
            String line
    ) {

        if (line == null
                || line.isBlank()) {
            return null;
        }

        String[] parts =
                line.split(
                        "\\|",
                        -1
                );

        if (parts.length < 7) {
            return null;
        }

        try {

            return new HistoryEntry(
                    Instant.parse(
                            parts[0]
                    ),
                    Integer.parseInt(
                            parts[1]
                    ),
                    Integer.parseInt(
                            parts[2]
                    ),
                    Integer.parseInt(
                            parts[3]
                    ),
                    Integer.parseInt(
                            parts[4]
                    ),
                    Integer.parseInt(
                            parts[5]
                    ),
                    Integer.parseInt(
                            parts[6]
                    )
            );

        } catch (Exception ignored) {

            return null;
        }
    }

    public record HistoryEntry(
            Instant timestamp,
            int averageScore,
            int knowledgeScore,
            int reasoningScore,
            int researchScore,
            int codingScore,
            int memoryScore
    ) {
    }
}
