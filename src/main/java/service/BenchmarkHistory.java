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
            Path.of("blackwater-benchmark-history.txt");

    public synchronized void record(
            BenchmarkEngine.BenchmarkSummary summary
    ) {

        if (summary == null) {
            return;
        }

        try {

            String entry =
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

            Files.writeString(
                    HISTORY_FILE,
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException ignored) {
        }
    }

    public synchronized List<HistoryEntry> getHistory() {

        if (!Files.exists(
                HISTORY_FILE
        )) {

            return List.of();
        }

        try {

            List<String> lines =
                    Files.readAllLines(
                            HISTORY_FILE,
                            StandardCharsets.UTF_8
                    );

            List<HistoryEntry> result =
                    new ArrayList<>();

            for (String line : lines) {

                HistoryEntry entry =
                        parse(line);

                if (entry != null) {
                    result.add(entry);
                }
            }

            return result;

        } catch (IOException e) {

            return List.of();
        }
    }

    public synchronized HistoryEntry getLatest() {

        List<HistoryEntry> history =
                getHistory();

        if (history.isEmpty()) {
            return null;
        }

        return history.get(
                history.size() - 1
        );
    }

    public synchronized int getBestAverage() {

        List<HistoryEntry> history =
                getHistory();

        int best = 0;

        for (HistoryEntry entry :
                history) {

            best =
                    Math.max(
                            best,
                            entry.averageScore()
                    );
        }

        return best;
    }

    public synchronized int getPreviousAverage() {

        List<HistoryEntry> history =
                getHistory();

        if (history.size() < 2) {
            return 0;
        }

        return history.get(
                history.size() - 2
        ).averageScore();
    }

    private HistoryEntry parse(
            String line
    ) {

        if (line == null
                || line.isBlank()) {

            return null;
        }

        try {

            String[] parts =
                    line.split(
                            "\\|"
                    );

            if (parts.length < 7) {
                return null;
            }

            return new HistoryEntry(
                    parts[0],
                    parseInt(parts[1]),
                    parseInt(parts[2]),
                    parseInt(parts[3]),
                    parseInt(parts[4]),
                    parseInt(parts[5]),
                    parseInt(parts[6])
            );

        } catch (Exception e) {

            return null;
        }
    }

    private int parseInt(
            String value
    ) {

        try {

            return Integer.parseInt(
                    value.trim()
            );

        } catch (Exception e) {

            return 0;
        }
    }

    public record HistoryEntry(
            String timestamp,
            int averageScore,
            int knowledgeScore,
            int reasoningScore,
            int researchScore,
            int codingScore,
            int memoryScore
    ) {
    }
}
