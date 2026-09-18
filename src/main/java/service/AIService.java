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
        return generate(message, "swift");
    }

    public String generate(String message, String mode) {

        if (message == null || message.isBlank()) {
            return "Message is empty.";
        }

        String providerName = System.getenv()
                .getOrDefault("AI_PROVIDER", "openai")
                .toLowerCase()
                .trim();

        String selectedMode = normalizeMode(mode);

        for (AIProvider provider : providers) {

            String className = provider.getClass()
                    .getSimpleName()
                    .toLowerCase();

            if (!className.startsWith(providerName)) {
                continue;
            }

            if (provider instanceof OpenAIProvider openAIProvider) {
                return openAIProvider.generate(
                        message,
                        selectedMode
                );
            }

            if (provider instanceof GeminiProvider geminiProvider) {
                return geminiProvider.generate(
                        message,
                        selectedMode
                );
            }

            if (provider instanceof AnthropicProvider anthropicProvider) {
                return anthropicProvider.generate(
                        message,
                        selectedMode
                );
            }

            if (provider instanceof CustomAIProvider customAIProvider) {
                return customAIProvider.generate(
                        message,
                        selectedMode
                );
            }

            return provider.generate(message);
        }

        return "AI provider not configured: " + providerName;
    }

    private String normalizeMode(String mode) {

        if (mode == null || mode.isBlank()) {
            return "swift";
        }

        return switch (mode.toLowerCase().trim()) {

            case "swift", "fast" ->
                    "swift";

            case "deep" ->
                    "deep";

            case "prime", "powerful" ->
                    "prime";

            default ->
                    "swift";
        };
    }
}
