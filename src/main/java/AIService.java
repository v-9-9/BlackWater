package service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIService {

    private final List<AIProvider> providers;

    public AIService(List<AIProvider> providers) {
        this.providers = providers;
    }

    public String generate(String message) {
        return generate(message, "fast");
    }

    public String generate(String message, String mode) {

        if (message == null || message.isBlank()) {
            return "Message is empty.";
        }

        String providerName = System.getenv()
                .getOrDefault("AI_PROVIDER", "openai")
                .toLowerCase();

        String selectedMode = mode == null || mode.isBlank()
                ? "fast"
                : mode.toLowerCase();

        for (AIProvider provider : providers) {

            String className = provider.getClass()
                    .getSimpleName()
                    .toLowerCase();

            if (className.startsWith(providerName)) {

                if (provider instanceof OpenAIProvider openAIProvider) {
                    return openAIProvider.generate(message, selectedMode);
                }

                return provider.generate(message);
            }
        }

        return "AI provider not configured: " + providerName;
    }
}
