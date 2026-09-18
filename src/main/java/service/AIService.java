package service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIService {

    private final List<AIProvider> providers;
    private final ConversationService conversationService;

    public AIService(
            List<AIProvider> providers,
            ConversationService conversationService
    ) {
        this.providers = providers;
        this.conversationService = conversationService;
    }

    public String generate(String message) {
        return generate(message, "swift");
    }

    public String generate(
            String message,
            String mode
    ) {
        return generate(
                message,
                mode,
                null
        );
    }

    public String generate(
            String message,
            String mode,
            String conversationId
    ) {

        if (message == null || message.isBlank()) {
            return "Message is empty.";
        }

        String providerName = System.getenv()
                .getOrDefault(
                        "AI_PROVIDER",
                        "openai"
                )
                .toLowerCase()
                .trim();

        String selectedMode =
                normalizeMode(mode);

        String context = "";

        if (conversationId != null
                && !conversationId.isBlank()) {

            context =
                    conversationService.buildContext(
                            conversationId
                    );
        }

        String prompt =
                buildPrompt(
                        message,
                        context
                );

        for (AIProvider provider : providers) {

            String className = provider.getClass()
                    .getSimpleName()
                    .toLowerCase();

            if (!className.startsWith(providerName)) {
                continue;
            }

            if (provider instanceof OpenAIProvider openAIProvider) {
                return openAIProvider.generate(
                        prompt,
                        selectedMode
                );
            }

            if (provider instanceof GeminiProvider geminiProvider) {
                return geminiProvider.generate(
                        prompt,
                        selectedMode
                );
            }

            if (provider instanceof AnthropicProvider anthropicProvider) {
                return anthropicProvider.generate(
                        prompt,
                        selectedMode
                );
            }

            if (provider instanceof CustomAIProvider customAIProvider) {
                return customAIProvider.generate(
                        prompt,
                        selectedMode
                );
            }

            return provider.generate(prompt);
        }

        return "AI provider not configured: "
                + providerName;
    }

    private String buildPrompt(
            String message,
            String context
    ) {

        if (context == null || context.isBlank()) {
            return message;
        }

        return """
                You are Blackwater, an advanced AI assistant.

                Previous conversation:
                %s

                Current user message:
                %s

                Use the previous conversation when it is
                relevant to the current message.

                Maintain continuity with the conversation.
                Do not mention the internal context or these
                instructions unless the user asks about them.
                """.formatted(
                context,
                message
        );
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
