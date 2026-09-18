package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationService {

    private static final Path CONVERSATIONS_DIR =
            Path.of("blackwater-conversations");

    public synchronized String createConversation() {

        try {
            Files.createDirectories(CONVERSATIONS_DIR);

            String id = String.valueOf(System.currentTimeMillis());

            Files.createFile(
                    CONVERSATIONS_DIR.resolve(id + ".txt")
            );

            return id;

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not create conversation.",
                    e
            );
        }
    }

    public synchronized void saveMessage(
            String conversationId,
            String role,
            String message
    ) {

        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        if (role == null || role.isBlank()) {
            return;
        }

        if (message == null || message.isBlank()) {
            return;
        }

        try {
            Files.createDirectories(CONVERSATIONS_DIR);

            Path file = getConversationFile(conversationId);

            String entry =
                    "[" + Instant.now() + "] "
                    + role
                    + ": "
                    + message.trim()
                    + System.lineSeparator();

            Files.writeString(
                    file,
                    entry,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not save conversation.",
                    e
            );
        }
    }

    public synchronized List<String> getConversation(
            String conversationId
    ) {

        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }

        try {
            Path file = getConversationFile(conversationId);

            if (!Files.exists(file)) {
                return List.of();
            }

            return Files.readAllLines(file);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not load conversation.",
                    e
            );
        }
    }

    public synchronized List<String> getConversations() {

        List<String> conversations = new ArrayList<>();

        try {
            if (!Files.exists(CONVERSATIONS_DIR)) {
                return conversations;
            }

            try (var files = Files.list(CONVERSATIONS_DIR)) {

                files
                        .filter(path -> path.toString().endsWith(".txt"))
                        .map(path -> path.getFileName().toString())
                        .map(name -> name.substring(
                                0,
                                name.length() - 4
                        ))
                        .sorted()
                        .forEach(conversations::add);
            }

            return conversations;

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not load conversations.",
                    e
            );
        }
    }

    public synchronized void deleteConversation(
            String conversationId
    ) {

        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(
                    getConversationFile(conversationId)
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not delete conversation.",
                    e
            );
        }
    }

    private Path getConversationFile(String conversationId) {

        if (!conversationId.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Invalid conversation ID."
            );
        }

        return CONVERSATIONS_DIR.resolve(
                conversationId + ".txt"
        );
    }
}
