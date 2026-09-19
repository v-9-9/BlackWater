package controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import service.AIService;
import service.AttachmentService;
import service.ConversationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> chat(
            @RequestBody ChatRequest request
    ) {
        try {
            String message = request.message() == null
                    ? ""
                    : request.message().trim();

            if (message.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Message cannot be empty.")
                );
            }

            String mode = normalizeMode(request.mode());

            String conversationId =
                    normalizeConversationId(request.conversationId());

            if (conversationId == null) {
                conversationId =
                        conversationService.createConversation();
            }

            conversationService.saveMessage(
                    conversationId,
                    "user",
                    message
            );

            String response =
                    aiService.generate(
                            message,
                            mode,
                            conversationId
                    );

            conversationService.saveMessage(
                    conversationId,
                    "assistant",
                    response
            );

            return ResponseEntity.ok(
                    Map.of(
                            "response", response,
                            "conversationId", conversationId,
                            "mode", mode
                    )
            );

        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "error",
                                    "Could not process chat request."
                            )
                    );
        }
    }

    @PostMapping(
            value = "/chat/with-attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> chatWithAttachments(
            @RequestParam(value = "message", required = false)
            String message,

            @RequestParam(value = "mode", required = false)
            String mode,

            @RequestParam(
                    value = "conversationId",
                    required = false
            )
            String conversationId,

            @RequestParam(
                    value = "attachmentIds",
                    required = false
            )
            List<String> attachmentIds,

            @RequestPart(
                    value = "files",
                    required = false
            )
            List<MultipartFile> files
    ) {
        try {
            String cleanMessage =
                    message == null
                            ? ""
                            : message.trim();

            String cleanMode =
                    normalizeMode(mode);

            String cleanConversationId =
                    normalizeConversationId(conversationId);

            if (cleanConversationId == null) {
                cleanConversationId =
                        conversationService.createConversation();
            }

            List<String> resolvedAttachmentIds =
                    new ArrayList<>();

            if (attachmentIds != null) {
                for (String id : attachmentIds) {
                    if (id == null || id.isBlank()) {
                        continue;
                    }

                    if (attachmentService.exists(id.trim())) {
                        resolvedAttachmentIds.add(
                                id.trim()
                        );
                    }
                }
            }

            if (files != null) {
                for (MultipartFile file : files) {
                    if (file == null || file.isEmpty()) {
                        continue;
                    }

                    AttachmentService.Attachment attachment =
                            attachmentService.save(file);

                    resolvedAttachmentIds.add(
                            attachment.id()
                    );
                }
            }

            if (cleanMessage.isBlank()
                    && resolvedAttachmentIds.isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "error",
                                "Message or attachment is required."
                        )
                );
            }

            String attachmentContext =
                    buildAttachmentContext(
                            resolvedAttachmentIds
                    );

            String prompt =
                    buildAttachmentPrompt(
                            cleanMessage,
                            attachmentContext
                    );

            conversationService.saveMessage(
                    cleanConversationId,
                    "user",
                    cleanMessage.isBlank()
                            ? "[Attachment]"
                            : cleanMessage
            );

            String response =
                    aiService.generate(
                            prompt,
                            cleanMode,
                            cleanConversationId
                    );

            conversationService.saveMessage(
                    cleanConversationId,
                    "assistant",
                    response
            );

            return ResponseEntity.ok(
                    Map.of(
                            "response", response,
                            "conversationId",
                            cleanConversationId,
                            "mode", cleanMode,
                            "attachmentIds",
                            resolvedAttachmentIds
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            e.getMessage() == null
                                    ? "Invalid attachment."
                                    : e.getMessage()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "error",
                                    "Could not process attachments."
                            )
                    );
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> conversations() {
        try {
            return ResponseEntity.ok(
                    conversationService.list()
            );

        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "error",
                                    "Could not load conversations."
                            )
                    );
        }
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> getConversation(
            @PathVariable String id
    ) {
        try {
            return ResponseEntity.ok(
                    conversationService.get(id)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            e.getMessage() == null
                                    ? "Invalid conversation."
                                    : e.getMessage()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity
                    .notFound()
                    .body(
                            Map.of(
                                    "error",
                                    "Conversation not found."
                            )
                    );
        }
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable String id
    ) {
        try {
            boolean deleted =
                    conversationService.delete(id);

            if (!deleted) {
                return ResponseEntity
                        .notFound()
                        .body(
                                Map.of(
                                        "error",
                                        "Conversation not found."
                                )
                        );
            }

            return ResponseEntity.ok(
                    Map.of(
                            "deleted",
                            true,
                            "conversationId",
                            id
                    )
            );

        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "error",
                                    "Could not delete conversation."
                            )
                    );
        }
    }

    private String buildAttachmentContext(
            List<String> attachmentIds
    ) {
        if (attachmentIds == null
                || attachmentIds.isEmpty()) {
            return "";
        }

        StringBuilder context =
                new StringBuilder();

        context.append(
                "\n\nATTACHMENTS PROVIDED BY USER:\n"
        );

        for (String id : attachmentIds) {
            try {
                AttachmentService.AttachmentInfo info =
                        attachmentService.get(id);

                context.append(
                        "\n--- Attachment: "
                );

                context.append(
                        info.originalName()
                );

                context.append(" ---\n");

                context.append(
                        "Type: "
                );

                context.append(
                        info.type()
                );

                context.append("\n");

                context.append(
                        "Content type: "
                );

                context.append(
                        info.contentType()
                );

                context.append("\n");

                if (attachmentService.isText(id)) {
                    String text =
                            attachmentService.readText(id);

                    context.append(
                            "Text content:\n"
                    );

                    context.append(
                            limitText(text)
                    );

                    context.append("\n");
                } else if (attachmentService.isImage(id)) {
                    context.append(
                            "This is an image attachment. "
                    );

                    context.append(
                            "Image understanding requires "
                    );

                    context.append(
                            "a multimodal AI provider."
                    );

                    context.append("\n");
                } else {
                    context.append(
                            "Binary/document attachment "
                    );

                    context.append(
                            "is available for processing."
                    );

                    context.append("\n");
                }

            } catch (Exception ignored) {
                context.append(
                        "\nAttachment could not be read: "
                );

                context.append(id);

                context.append("\n");
            }
        }

        return context.toString();
    }

    private String buildAttachmentPrompt(
            String message,
            String attachmentContext
    ) {
        StringBuilder prompt =
                new StringBuilder();

        if (!message.isBlank()) {
            prompt.append(message);
        } else {
            prompt.append(
                    "Analyze the provided attachment(s) "
                            + "and explain the useful information."
            );
        }

        if (!attachmentContext.isBlank()) {
            prompt.append(
                    "\n\nUse the following attachment "
                            + "information when answering:\n"
            );

            prompt.append(
                    attachmentContext
            );
        }

        return prompt.toString();
    }

    private String limitText(String value) {
        if (value == null) {
            return "";
        }

        int max =
                120_000;

        if (value.length() <= max) {
            return value;
        }

        return value.substring(0, max)
                + "\n[Attachment text truncated.]";
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "normal";
        }

        String normalized =
                mode.trim().toLowerCase();

        return switch (normalized) {
            case "normal",
                    "deep",
                    "prime",
                    "research" ->
                    normalized;

            default ->
                    "normal";
        };
    }

    private String normalizeConversationId(
            String conversationId
    ) {
        if (conversationId == null
                || conversationId.isBlank()) {
            return null;
        }

        String id =
                conversationId.trim();

        if (!id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Invalid conversation ID."
            );
        }

        return id;
    }

    public record ChatRequest(
            String message,
            String mode,
            String conversationId
    ) {
    }
}
