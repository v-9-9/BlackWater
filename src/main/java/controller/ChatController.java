package controller;

import org.springframework.web.bind.annotation.*;
import service.AIService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final AIService aiService;

    public ChatController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public String chat(
            @RequestBody Map<String, String> request
    ) {
        String message = request.get("message");
        String mode = request.getOrDefault("mode", "fast");

        return aiService.generate(message, mode);
    }
}
