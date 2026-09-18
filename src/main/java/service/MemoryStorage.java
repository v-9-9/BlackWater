package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class MemoryStorage {

    private static final Path MEMORY_FILE =
            Path.of("blackwater-memory.txt");

    public synchronized void save(String information) {

        if (information == null || information.isBlank()) {
            return;
        }

        try {
            Files.writeString(
                    MEMORY_FILE,
                    information.trim() + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new RuntimeException("Could not save memory.", e);
        }
    }

    public synchronized List<String> load() {

        try {

            if (!Files.exists(MEMORY_FILE)) {
                return List.of();
            }

            return Files.readAllLines(MEMORY_FILE);

        } catch (IOException e) {
            throw new RuntimeException("Could not load memory.", e);
        }
    }
}
