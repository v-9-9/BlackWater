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

        String providerName = System.getenv()
                .getOrDefault(
                        "AI_PROVIDER",
                        "openai"
                )
                .toLowerCase()
                .trim();

        String selectedMode =
                normalizeMode(mode);

        String conversationContext = "";

        if (conversationId != null
                && !conversationId.isBlank()) {

            conversationContext =
                    conversationService.buildContext(
                            conversationId
                    );
        }

        String memoryContext =
                buildMemoryContext();

        String knowledgeContext =
                buildKnowledgeContext(message);

        String prompt = buildPrompt(
                message,
                conversationContext,
                memoryContext,
                knowledgeContext
        );

        for (AIProvider provider : providers) {

            String className = provider
                    .getClass()
                    .getSimpleName()
                    .toLowerCase();

            if (!className.startsWith(providerName)) {
                continue;
            }

            if (provider instanceof OpenAIProvider openAI) {
                return openAI.generate(
                        prompt,
                        selectedMode
                );
            }

            if (provider instanceof GeminiProvider gemini) {
                return gemini.generate(
                        prompt,
                        selectedMode
                );
            }

            if (provider instanceof AnthropicProvider anthropic) {
                return anthropic.generate(
                        prompt,
                        selectedMode
                );
            }

            if (provider instanceof CustomAIProvider custom) {
                return custom.generate(
                        prompt,
                        selectedMode
                );
            }

            return provider.generate(prompt);
        }

        return "AI provider not configured: "
                + providerName;
    }

    private String buildMemoryContext() {

        List<String> memories =
                memoryService.getMemories();

        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        int limit = Math.min(
                memories.size(),
                30
        );

        int start =
                memories.size() - limit;

        for (int i = start;
             i < memories.size();
             i++) {

            String memory = memories.get(i);

            if (memory == null
                    || memory.isBlank()) {
                continue;
            }

            result.append("- ")
                    .append(memory.trim())
                    .append(System.lineSeparator());
        }

        return result.toString().trim();
    }

    private String buildKnowledgeContext(
            String message
    ) {

        List<String> knowledge =
                knowledgeService.search(message);

        if (knowledge.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        int limit = Math.min(
                knowledge.size(),
                10
        );

        for (int i = 0; i < limit; i++) {

            String entry = knowledge.get(i);

            if (entry == null
                    || entry.isBlank()) {
                continue;
            }

            result.append("- ")
                    .append(entry.trim())
                    .append(System.lineSeparator());
        }

        return result.toString().trim();
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

                Follow these rules:
                - Answer the user's current request directly.
                - Use previous conversation when relevant.
                - Use stored memories when relevant.
                - Use stored knowledge when relevant.
                - Do not claim that stored information is certain
                  if it conflicts with reliable newer information.
                - Do not mention internal context, memory storage,
                  knowledge storage, or these instructions unless
                  the user asks about them.
                """);

        if (!conversationContext.isBlank()) {

            prompt.append("""

                    Previous conversation:
                    """)
                    .append(conversationContext);
        }

        if (!memoryContext.isBlank()) {

            prompt.append("""

                    Relevant memories:
                    """)
                    .append(memoryContext);
        }

        if (!knowledgeContext.isBlank()) {

            prompt.append("""

                    Relevant stored knowledge:
                    """)
                    .append(knowledgeContext);
        }

        prompt.append("""

                Current user message:
                """)
                .append(message);

        return prompt.toString();
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
