package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class CustomAIProvider implements AIProvider {

    private final WebClient webClient;

    public CustomAIProvider(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public String generate(String message) {
        return generate(message, "fast");
    }

    public String generate(String message, String mode) {

        String apiKey = System.getenv("CUSTOM_AI_API_KEY");
        String baseUrl = System.getenv("CUSTOM_AI_BASE_URL");

        if (baseUrl == null || baseUrl.isBlank()) {
            return "CUSTOM_AI_BASE_URL is not configured.";
        }

        String model = getModel(mode);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                }
        );

        var request = webClient.post()
                .uri(baseUrl)
                .header("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        return request
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String getModel(String mode) {

        String defaultModel = System.getenv()
                .getOrDefault("CUSTOM_AI_MODEL", "default");

        return switch (mode == null ? "fast" : mode.toLowerCase()) {
            case "deep" -> System.getenv()
                    .getOrDefault("CUSTOM_AI_DEEP_MODEL", defaultModel);

            case "powerful" -> System.getenv()
                    .getOrDefault("CUSTOM_AI_POWERFUL_MODEL", defaultModel);

            default -> defaultModel;
        };
    }
}
