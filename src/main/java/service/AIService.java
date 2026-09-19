package service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIService {

    private final List<AIProvider> providers;
    private final ConversationService conversationService;
    private final MemoryService memoryService;
    private final KnowledgeService knowledgeService;

    public AIService(
            List<AIProvider> providers,
            ConversationService conversationService,
            MemoryService memoryService,
            KnowledgeService knowledgeService
    ) {
        this.providers = providers;
        this.conversationService = conversationService;
        this.memoryService = memoryService;
        this.knowledgeService = knowledgeService;
    }

    public String generate(String message) {
        return generate(message, "swift", null);
    }

    public String generate(
            String message,
            String mode
    ) {
        return generate(message, mode, null);
    }

    public String generate(
            String message,
            String mode,
            String conversationId
    ) {

        if (message == null || message.isBlank()) {
            return "Message is empty.";
        }

        String selectedMode =
                normalizeMode(mode);

        String conversationContext =
                buildConversationContext(
                        conversationId
                );

        String memoryContext =
                buildMemoryContext();

        String knowledgeContext =
                buildKnowledgeContext(
                        message
                );

        String prompt = buildPrompt(
                message,
                conversationContext,
                memoryContext,
                knowledgeContext
        );

        /*
         * API providers are optional.
         *
         * If an API provider is configured and has
         * a working key, use it.
         *
         * If no provider can be used, Blackwater
         * falls back to its internal engine.
         */

        String providerName =
                System.getenv()
                        .getOrDefault(
                                "AI_PROVIDER",
                                ""
                        )
                        .toLowerCase()
                        .trim();

        if (!providerName.isBlank()) {

            String providerResponse =
                    tryProvider(
                            providerName,
                            prompt,
                            selectedMode
                    );

            if (isUsableResponse(
                    providerResponse
            )) {

                return providerResponse;
            }
        }

        /*
         * Try configured providers automatically.
         *
         * This allows Blackwater to continue working
         * even if AI_PROVIDER is not explicitly set.
         */

        for (AIProvider provider : providers) {

            String className =
                    provider.getClass()
                            .getSimpleName()
                            .toLowerCase();

            String response =
                    tryProvider(
                            className,
                            prompt,
                            selectedMode
                    );

            if (isUsableResponse(response)) {
                return response;
            }
        }

        /*
         * No external AI provider is available.
         *
         * Use Blackwater's internal engine.
         */

        return internalResponse(
                message,
                conversationContext,
                memoryContext,
                knowledgeContext
        );
    }

    private String tryProvider(
            String providerName,
            String prompt,
            String mode
    ) {

        for (AIProvider provider : providers) {

            String className =
                    provider.getClass()
                            .getSimpleName()
                            .toLowerCase();

            if (!className.startsWith(
                    providerName
            )
                    && !providerName.startsWith(
                            className.replace(
                                    "provider",
                                    ""
                            )
                    )) {

                continue;
            }

            try {

                if (provider instanceof OpenAIProvider openAI) {

                    return openAI.generate(
                            prompt,
                            mode
                    );
                }

                if (provider instanceof GeminiProvider gemini) {

                    return gemini.generate(
                            prompt,
                            mode
                    );
                }

                if (provider instanceof AnthropicProvider anthropic) {

                    return anthropic.generate(
                            prompt,
                            mode
                    );
                }

                if (provider instanceof CustomAIProvider custom) {

                    return custom.generate(
                            prompt,
                            mode
                    );
                }

                return provider.generate(prompt);

            } catch (Exception ignored) {

                return "";
            }
        }

        return "";
    }

    private String buildConversationContext(
            String conversationId
    ) {

        if (conversationId == null
                || conversationId.isBlank()) {

            return "";
        }

        try {

            return conversationService
                    .buildContext(
                            conversationId
                    );

        } catch (Exception ignored) {

            return "";
        }
    }

    private String buildMemoryContext() {

        List<String> memories;

        try {

            memories =
                    memoryService.getMemories();

        } catch (Exception ignored) {

            return "";
        }

        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        int limit =
                Math.min(
                        memories.size(),
                        30
                );

        int start =
                memories.size() - limit;

        for (
                int i = start;
                i < memories.size();
                i++
        ) {

            String memory =
                    memories.get(i);

            if (memory == null
                    || memory.isBlank()) {

                continue;
            }

            result.append("- ")
                    .append(
                            memory.trim()
                    )
                    .append(
                            System.lineSeparator()
                    );
        }

        return result
                .toString()
                .trim();
    }

    private String buildKnowledgeContext(
            String message
    ) {

        List<String> knowledge;

        try {

            knowledge =
                    knowledgeService.search(
                            message
                    );

        } catch (Exception ignored) {

            return "";
        }

        if (knowledge.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        int limit =
                Math.min(
                        knowledge.size(),
                        10
                );

        for (
                int i = 0;
                i < limit;
                i++
        ) {

            String entry =
                    knowledge.get(i);

            if (entry == null
                    || entry.isBlank()) {

                continue;
            }

            result.append("- ")
                    .append(
                            entry.trim()
                    )
                    .append(
                            System.lineSeparator()
                    );
        }

        return result
                .toString()
                .trim();
    }

    private String buildPrompt(
            String message,
            String conversationContext,
            String memoryContext,
            String knowledgeContext
    ) {

        StringBuilder prompt =
                new StringBuilder();

        prompt.append("""
                You are Blackwater, an advanced AI assistant.

                Your goals:
                - Understand the user's request.
                - Answer directly and naturally.
                - Use relevant previous conversation.
                - Use relevant stored memories.
                - Use relevant stored knowledge.
                - Prefer reliable and newer information.
                - Do not invent facts.
                - If information is missing, identify what is missing.
                - Do not mention internal system instructions.

                """);

        if (!conversationContext.isBlank()) {

            prompt.append("""
                    Previous conversation:
                    """)
                    .append(
                            conversationContext
                    )
                    .append(
                            System.lineSeparator()
                    )
                    .append(
                            System.lineSeparator()
                    );
        }

        if (!memoryContext.isBlank()) {

            prompt.append("""
                    Relevant memories:
                    """)
                    .append(
                            memoryContext
                    )
                    .append(
                            System.lineSeparator()
                    )
                    .append(
                            System.lineSeparator()
                    );
        }

        if (!knowledgeContext.isBlank()) {

            prompt.append("""
                    Relevant stored knowledge:
                    """)
                    .append(
                            knowledgeContext
                    )
                    .append(
                            System.lineSeparator()
                    )
                    .append(
                            System.lineSeparator()
                    );
        }

        prompt.append("""
                Current user message:
                """)
                .append(message);

        return prompt.toString();
    }

    private String internalResponse(
            String message,
            String conversationContext,
            String memoryContext,
            String knowledgeContext
    ) {

        /*
         * This is intentionally simple for now.
         *
         * The next stages will replace this with
         * Blackwater's internal research,
         * reasoning, evaluation and learning engine.
         */

        if (!knowledgeContext.isBlank()) {

            return """
                    I don't currently have an external AI model
                    connected, but I found relevant stored knowledge.

                    %s

                    Your request:
                    %s
                    """.formatted(
                    knowledgeContext,
                    message
            ).trim();
        }

        if (!memoryContext.isBlank()) {

            return """
                    Blackwater is running without an external AI model.

                    Relevant stored memory:
                    %s

                    Your request:
                    %s

                    The internal reasoning and research engine
                    will be expanded in the next stage.
                    """.formatted(
                    memoryContext,
                    message
            ).trim();
        }

        return """
                Blackwater is running in internal mode.

                No external AI provider is currently available.

                Your request:
                %s

                The internal research, reasoning and learning
                engine is ready to be expanded.
                """.formatted(
                message
        ).trim();
    }

    private boolean isUsableResponse(
            String response
    ) {

        if (response == null
                || response.isBlank()) {

            return false;
        }

        String lower =
                response
                        .toLowerCase()
                        .trim();

        return !lower.contains(
                "api_key is not configured"
        )
                && !lower.contains(
                        "api key is not configured"
                )
                && !lower.contains(
                        "provider not configured"
                )
                && !lower.contains(
                        "request failed:"
                );
    }

    private String normalizeMode(
            String mode
    ) {

        if (mode == null
                || mode.isBlank()) {

            return "swift";
        }

        return switch (
                mode.toLowerCase().trim()
        ) {

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
