package service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIService {

    private final List<AIProvider> providers;
    private final MemoryService memoryService;

    public AIService(
            List<AIProvider> providers,
            MemoryService memoryService
    ) {
        this.providers = providers;
        this.memoryService = memoryService;
    }

    public String generate(String message) {

        List<String> memories = memoryService.getMemories();

        StringBuilder context = new StringBuilder();

        if (!memories.isEmpty()) {
            context.append("Known information:\n");

            for (String memory : memories) {
                context.append("- ")
                        .append(memory)
                        .append("\n");
            }

            context.append("\n");
        }

        context.append("User message:\n")
                .append(message);

        String response = generateFromProvider(context.toString());

        memoryService.remember(
                "User: " + message + "\nAI: " + response
        );

        return response;
    }

    private String generateFromProvider(String message) {

        String providerName = System.getenv()
                .getOrDefault("AI_PROVIDER", "openai")
                .toLowerCase();

        for (AIProvider provider : providers) {

            String className = provider.getClass()
                    .getSimpleName()
                    .toLowerCase();

            if (className.startsWith(providerName)) {
                return provider.generate(message);
            }
        }

        return "AI provider not configured: " + providerName;
    }
}
