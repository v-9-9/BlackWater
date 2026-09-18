package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class OpenAIProvider implements AIProvider {

    private final WebClient webClient;

    public OpenAIProvider(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public String generate(String message) {
        return generate(message, "fast");
    }

    public String generate(String message, String mode) {

        String apiKey = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "OPENAI_API_KEY is not configured.";
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

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String getModel(String mode) {

        String defaultModel = System.getenv()
                .getOrDefault("OPENAI_MODEL", "gpt-4o-mini");

        return switch (mode == null ? "fast" : mode.toLowerCase()) {
            case "deep" -> System.getenv()
                    .getOrDefault("OPENAI_DEEP_MODEL", defaultModel);

            case "powerful" -> System.getenv()
                    .getOrDefault("OPENAI_POWERFUL_MODEL", defaultModel);

            default -> defaultModel;
        };
    }
}
