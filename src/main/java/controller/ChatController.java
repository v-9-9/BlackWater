package controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.AIService;
import service.AttachmentService;
import service.ConversationService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AIService aiService;
    private final ConversationService conversationService;
    private final AttachmentService attachmentService;

    public ChatController(
            AIService aiService,
            ConversationService conversationService,
            AttachmentService attachmentService
    ) {
        this.aiService = aiService;
        this.conversationService = conversationService;
        this.attachmentService = attachmentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        try {
            String message = request.message() == null ? "" : request.message().trim();

            if (message.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Message cannot be empty"
                ));
            }

            String conversationId = normalizeConversationId(request.conversationId());
            String mode = normalizeMode(request.mode());

            String response = aiService.generate(
                    message,
                    mode,
                    conversationId
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "conversationId", conversationId,
                    "mode", mode,
                    "message", response
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null ? "Chat failed" : e.getMessage()
            ));
        }
    }

    @PostMapping(
            value = "/chat/with-attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> chatWithAttachments(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "attachmentIds", required = false) String attachmentIds
    ) {
        try {
            String cleanMessage = message == null ? "" : message.trim();
            String cleanMode = normalizeMode(mode);
            String cleanConversationId = normalizeConversationId(conversationId);

            List<String> ids = parseAttachmentIds(attachmentIds);

            if (cleanMessage.isEmpty() && ids.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Message or attachment is required"
                ));
            }

            List<AttachmentService.AttachmentInfo> attachments = new ArrayList<>();
            List<String> attachmentContext = new ArrayList<>();

            for (String id : ids) {
                if (!attachmentService.exists(id)) {
                    continue;
                }

                AttachmentService.AttachmentInfo info = attachmentService.get(id);
                attachments.add(info);

                if (attachmentService.isText(id)) {
                    String text = attachmentService.readText(id);

                    if (text != null && !text.isBlank()) {
                        attachmentContext.add(
                                "FILE: " + info.originalName() + "\n" +
                                text
                        );
                    }
                } else if (attachmentService.isImage(id)) {
                    attachmentContext.add(
                            "IMAGE: " + info.originalName() +
                            "\nThe user attached this image. Image ID: " + id
                    );
                } else {
                    attachmentContext.add(
                            "ATTACHMENT: " + info.originalName() +
                            "\nType: " + info.type() +
                            "\nContent-Type: " + info.contentType()
                    );
                }
            }

            String enrichedMessage = buildAttachmentMessage(
                    cleanMessage,
                    attachmentContext
            );

            String response = aiService.generate(
                    enrichedMessage,
                    cleanMode,
                    cleanConversationId
            );

            List<Map<String, Object>> attachmentResults = attachments.stream()
                    .map(info -> Map.<String, Object>of(
                            "id", info.id(),
                            "name", info.originalName(),
                            "type", info.type(),
                            "size", info.size(),
                            "contentType", info.contentType(),
                            "url", "/api/attachments/" + info.id()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "conversationId", cleanConversationId,
                    "mode", cleanMode,
                    "message", response,
                    "attachments", attachmentResults
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null
                            ? "Chat with attachments failed"
                            : e.getMessage()
            ));
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> conversations() {
        try {
            return ResponseEntity.ok(conversationService.list());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null
                            ? "Unable to load conversations"
                            : e.getMessage()
            ));
        }
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> getConversation(@PathVariable String id) {
        try {
            return ResponseEntity.ok(
                    conversationService.get(id)
            );
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable String id) {
        try {
            boolean deleted = conversationService.delete(id);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "conversationId", id
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null
                            ? "Unable to delete conversation"
                            : e.getMessage()
            ));
        }
    }

    private String buildAttachmentMessage(
            String message,
            List<String> attachmentContext
    ) {
        if (attachmentContext.isEmpty()) {
            return message;
        }

        StringBuilder result = new StringBuilder();

        if (!message.isBlank()) {
            result.append(message);
        } else {
            result.append("Analyze the attached files and images.");
        }

        result.append("\n\n--- ATTACHMENTS ---\n");

        for (String context : attachmentContext) {
            result.append(context)
                    .append("\n\n");
        }

        result.append("--- END ATTACHMENTS ---");

        return result.toString();
    }

    private List<String> parseAttachmentIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .filter(id -> id.matches("[a-fA-F0-9\\-]{8,64}"))
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private String normalizeConversationId(String id) {
        if (id == null || id.isBlank()) {
            return conversationService.create();
        }

        String clean = id.trim();

        if (!clean.matches("[a-zA-Z0-9_-]{1,100}")) {
            return conversationService.create();
        }

        return clean;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "normal";
        }

        String clean = mode.trim().toLowerCase(Locale.ROOT);

        return switch (clean) {
            case "normal", "deep", "prime", "research", "coding" -> clean;
            default -> "normal";
        };
    }

    public record ChatRequest(
            String message,
            String mode,
            String conversationId
    ) {
    }
}
