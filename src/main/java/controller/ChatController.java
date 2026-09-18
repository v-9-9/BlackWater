package controller;

import org.springframework.web.bind.annotation.*;
import service.AIService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final AIService aiService;

    public ChatController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return aiService.generate(message);
    }
}
