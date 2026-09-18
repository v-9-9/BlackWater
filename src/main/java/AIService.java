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
