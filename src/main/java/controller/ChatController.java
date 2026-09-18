package controller;

import org.springframework.web.bind.annotation.*;
import service.AIService;
import service.ConversationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final AIService aiService;
    private final ConversationService conversationService;

    public ChatController(
            AIService aiService,
            ConversationService conversationService
    ) {
        this.aiService = aiService;
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    public String chat(
            @RequestBody Map<String, String> request
    ) {
        String message = request.get("message");
        String mode = request.getOrDefault("mode", "fast");
        String conversationId = request.get("conversationId");

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation();
        }

        conversationService.saveMessage(
                conversationId,
                "user",
                message
        );

        String response = aiService.generate(
                message,
                mode
        );

        conversationService.saveMessage(
                conversationId,
                "assistant",
                response
        );

        return response;
    }

    @PostMapping("/conversations")
    public Map<String, String> createConversation() {

        String id = conversationService.createConversation();

        return Map.of(
                "id", id
        );
    }

    @GetMapping("/conversations")
    public List<String> getConversations() {
        return conversationService.getConversations();
    }

    @GetMapping("/conversations/{id}")
    public List<String> getConversation(
            @PathVariable String id
    ) {
        return conversationService.getConversation(id);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, String> deleteConversation(
            @PathVariable String id
    ) {
        conversationService.deleteConversation(id);

        return Map.of(
                "status", "deleted"
        );
    }
}
