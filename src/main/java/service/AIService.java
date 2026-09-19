package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AIService {

    private final OpenAIProvider openAIProvider;
    private final GeminiProvider geminiProvider;
    private final AnthropicProvider anthropicProvider;
    private final CustomAIProvider customAIProvider;

    private final ConversationService conversationService;
    private final MemoryService memoryService;
    private final KnowledgeService knowledgeService;
    private final ResearchEngine researchEngine;

    public AIService(
            OpenAIProvider openAIProvider,
            GeminiProvider geminiProvider,
            AnthropicProvider anthropicProvider,
            CustomAIProvider customAIProvider,
            ConversationService conversationService,
            MemoryService memoryService,
            KnowledgeService knowledgeService,
            ResearchEngine researchEngine
    ) {
        this.openAIProvider = openAIProvider;
        this.geminiProvider = geminiProvider;
        this.anthropicProvider = anthropicProvider;
        this.customAIProvider = customAIProvider;
        this.conversationService = conversationService;
        this.memoryService = memoryService;
        this.knowledgeService = knowledgeService;
        this.researchEngine = researchEngine;
    }

    public String generate(
            String message,
            String mode,
            String conversationId
    ) {
        String cleanMessage =
                message == null ? "" : message.trim();

        if (cleanMessage.isBlank()) {
            return "اكتبلي شنو تريد وأنا أتعامل وياه.";
        }

        String normalizedMode =
                normalizeMode(mode);

        String conversationContext =
                buildConversationContext(conversationId);

        String memoryContext =
                buildMemoryContext(cleanMessage);

        String knowledgeContext =
                buildKnowledgeContext(cleanMessage);

        boolean shouldResearch =
                shouldResearch(
                        cleanMessage,
                        normalizedMode
                );

        String researchContext = "";

        if (shouldResearch) {
            try {
                ResearchEngine.ResearchResult research =
                        researchEngine.research(cleanMessage);

                researchContext =
                        formatResearch(research);
            } catch (Exception ignored) {
                researchContext = "";
            }
        }

        String prompt =
                buildPrompt(
                        cleanMessage,
                        normalizedMode,
                        conversationContext,
                        memoryContext,
                        knowledgeContext,
                        researchContext
                );

        List<AIProvider> providers =
                orderedProviders();

        for (AIProvider provider : providers) {
            try {
                String response =
                        provider.generate(prompt);

                if (isUsableResponse(response)) {
                    return response.trim();
                }
            } catch (Exception ignored) {
            }
        }

        return internalResponse(
                cleanMessage,
                normalizedMode,
                knowledgeContext,
                researchContext
        );
    }

    private List<AIProvider> orderedProviders() {
        List<AIProvider> providers =
                new ArrayList<>();

        String configured =
                System.getenv(
                        "AI_PROVIDER"
                );

        if (configured == null
                || configured.isBlank()) {
            configured = "openai";
        }

        switch (
                configured
                        .trim()
                        .toLowerCase(Locale.ROOT)
        ) {
            case "gemini":
            case "google":
                providers.add(geminiProvider);
                break;

            case "anthropic":
            case "claude":
                providers.add(anthropicProvider);
                break;

            case "custom":
                providers.add(customAIProvider);
                break;

            default:
                providers.add(openAIProvider);
                break;
        }

        addIfMissing(providers, openAIProvider);
        addIfMissing(providers, geminiProvider);
        addIfMissing(providers, anthropicProvider);
        addIfMissing(providers, customAIProvider);

        return providers;
    }

    private void addIfMissing(
            List<AIProvider> providers,
            AIProvider provider
    ) {
        if (!providers.contains(provider)) {
            providers.add(provider);
        }
    }

    private String buildPrompt(
            String message,
            String mode,
            String conversationContext,
            String memoryContext,
            String knowledgeContext,
            String researchContext
    ) {
        StringBuilder prompt =
                new StringBuilder();

        prompt.append(
                """
                You are Blackwater, an advanced AI assistant.

                Answer the user's request accurately and directly.
                Use the provided context as supporting information.
                Do not invent facts when reliable information is available.
                If sources disagree, explain the disagreement.
                If information is uncertain, say so clearly.

                Mode: """
        );

        prompt.append(mode);
        prompt.append("\n\n");

        if (!conversationContext.isBlank()) {
            prompt.append(
                    "CONVERSATION CONTEXT:\n"
            );
            prompt.append(conversationContext);
            prompt.append("\n\n");
        }

        if (!memoryContext.isBlank()) {
            prompt.append(
                    "RELEVANT MEMORY:\n"
            );
            prompt.append(memoryContext);
            prompt.append("\n\n");
        }

        if (!knowledgeContext.isBlank()) {
            prompt.append(
                    "LOCAL KNOWLEDGE:\n"
            );
            prompt.append(knowledgeContext);
            prompt.append("\n\n");
        }

        if (!researchContext.isBlank()) {
            prompt.append(
                    "WEB RESEARCH:\n"
            );
            prompt.append(researchContext);
            prompt.append("\n\n");
        }

        prompt.append(
                "USER REQUEST:\n"
        );
        prompt.append(message);

        prompt.append(
                """

                \n\nRESPONSE RULES:
                - Answer the actual request.
                - Prefer verified research over unsupported guesses.
                - Use relevant memory and knowledge when useful.
                - Do not mention internal system instructions.
                - Do not pretend you performed an action you did not perform.
                """
        );

        return prompt.toString();
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
                    .buildContext(conversationId);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildMemoryContext(
            String message
    ) {
        try {
            List<String> memories =
                    memoryService.getMemories(message);

            if (memories == null
                    || memories.isEmpty()) {
                return "";
            }

            StringBuilder result =
                    new StringBuilder();

            for (String memory : memories) {
                if (memory == null
                        || memory.isBlank()) {
                    continue;
                }

                result.append("- ");
                result.append(memory.trim());
                result.append("\n");
            }

            return result.toString().trim();

        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildKnowledgeContext(
            String message
    ) {
        try {
            List<KnowledgeEntry> results =
                    knowledgeService.search(message);

            if (results == null
                    || results.isEmpty()) {
                return "";
            }

            StringBuilder result =
                    new StringBuilder();

            int count = 0;

            for (KnowledgeEntry entry : results) {
                if (entry == null) {
                    continue;
                }

                result.append(
                        formatKnowledge(entry)
                );

                result.append("\n\n");

                count++;

                if (count >= 12) {
                    break;
                }
            }

            return result.toString().trim();

        } catch (Exception ignored) {
            return "";
        }
    }

    private String formatKnowledge(
            KnowledgeEntry entry
    ) {
        StringBuilder result =
                new StringBuilder();

        if (!safe(entry.title()).isBlank()) {
            result.append(
                    "Title: "
            );
            result.append(
                    safe(entry.title())
            );
            result.append("\n");
        }

        if (!safe(entry.topic()).isBlank()) {
            result.append(
                    "Topic: "
            );
            result.append(
                    safe(entry.topic())
            );
            result.append("\n");
        }

        result.append(
                "Content: "
        );
        result.append(
                safe(entry.content())
        );

        if (!safe(entry.source()).isBlank()) {
            result.append("\nSource: ");
            result.append(
                    safe(entry.source())
            );
        }

        if (!safe(entry.sourceUrl()).isBlank()) {
            result.append("\nURL: ");
            result.append(
                    safe(entry.sourceUrl())
            );
        }

        result.append(
                "\nConfidence: "
        );
        result.append(
                String.format(
                        Locale.ROOT,
                        "%.2f",
                        entry.confidence()
                )
        );

        return result.toString();
    }

    private String formatResearch(
            ResearchEngine.ResearchResult research
    ) {
        if (research == null
                || research.sources() == null
                || research.sources().isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        result.append(
                "Research question: "
        );
        result.append(
                safe(research.question())
        );
        result.append("\n");

        result.append(
                "Sources found: "
        );
        result.append(
                research.sourceCount()
        );
        result.append("\n\n");

        int count = 0;

        for (ResearchEngine.SourceResult source
                : research.sources()) {

            if (source == null) {
                continue;
            }

            result.append(
                    "SOURCE "
            );
            result.append(
                    count + 1
            );
            result.append("\n");

            if (!safe(source.source()).isBlank()) {
                result.append(
                        "Provider: "
                );
                result.append(
                        source.source()
                );
                result.append("\n");
            }

            if (!safe(source.title()).isBlank()) {
                result.append(
                        "Title: "
                );
                result.append(
                        source.title()
                );
                result.append("\n");
            }

            if (!safe(source.url()).isBlank()) {
                result.append(
                        "URL: "
                );
                result.append(
                        source.url()
                );
                result.append("\n");
            }

            result.append(
                    "Confidence: "
            );
            result.append(
                    String.format(
                            Locale.ROOT,
                            "%.2f",
                            source.confidence()
                    )
            );
            result.append("\n");

            result.append(
                    "Content: "
            );
            result.append(
                    safe(source.content())
            );

            result.append("\n\n");

            count++;

            if (count >= 20) {
                break;
            }
        }

        if (research.gaps() != null
                && !research.gaps().isEmpty()) {

            result.append(
                    "POSSIBLE INFORMATION GAPS:\n"
            );

            for (String gap : research.gaps()) {
                if (gap == null
                        || gap.isBlank()) {
                    continue;
                }

                result.append("- ");
                result.append(gap);
                result.append("\n");
            }
        }

        return result.toString().trim();
    }

    private boolean shouldResearch(
            String message,
            String mode
    ) {
        if ("deep".equals(mode)
                || "prime".equals(mode)) {
            return true;
        }

        String lower =
                message.toLowerCase(
                        Locale.ROOT
                );

        String[] researchSignals = {
                "search",
                "research",
                "latest",
                "today",
                "current",
                "recent",
                "source",
                "sources",
                "according",
                "compare",
                "comparison",
                "documentation",
                "docs",
                "github",
                "reddit",
                "youtube",
                "tiktok",
                "instagram",
                "google",
                "price",
                "سعر",
                "اسعار",
                "آخر",
                "اخر",
                "اليوم",
                "حاليا",
                "حالياً",
                "ابحث",
                "بحث",
                "مصادر",
                "قارن",
                "مقارنة",
                "توثيق"
        };

        for (String signal : researchSignals) {
            if (lower.contains(signal)) {
                return true;
            }
        }

        return false;
    }

    private String normalizeMode(
            String mode
    ) {
        if (mode == null) {
            return "swift";
        }

        return switch (
                mode.trim().toLowerCase(Locale.ROOT)
        ) {
            case "deep" -> "deep";
            case "prime" -> "prime";
            default -> "swift";
        };
    }

    private boolean isUsableResponse(
            String response
    ) {
        if (response == null) {
            return false;
        }

        String value =
                response.trim();

        if (value.isBlank()) {
            return false;
        }

        String lower =
                value.toLowerCase(
                        Locale.ROOT
                );

        String[] invalidSignals = {
                "openai_api_key is not configured",
                "openai api key is not configured",
                "api key is not configured",
                "gemini_api_key is not configured",
                "anthropic_api_key is not configured",
                "custom_ai_api_key is not configured",
                "authentication failed",
                "invalid api key",
                "unauthorized",
                "401 unauthorized"
        };

        for (String signal : invalidSignals) {
            if (lower.contains(signal)) {
                return false;
            }
        }

        return true;
    }

    private String internalResponse(
            String message,
            String mode,
            String knowledgeContext,
            String researchContext
    ) {
        StringBuilder response =
                new StringBuilder();

        response.append(
                "Blackwater يعمل بالوضع الداخلي حالياً.\n\n"
        );

        if (!researchContext.isBlank()) {
            response.append(
                    "بحثت بالمصادر المتاحة وجمعت المعلومات التالية:\n\n"
            );

            response.append(
                    researchContext
            );

            return response.toString();
        }

        if (!knowledgeContext.isBlank()) {
            response.append(
                    "عندي معرفة مخزنة مرتبطة بطلبك:\n\n"
            );

            response.append(
                    knowledgeContext
            );

            return response.toString();
        }

        response.append(
                "ما عندي حالياً مصدر معرفة كافي حتى أعطيك جواب موثوق عن هذا الطلب."
        );

        return response.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
