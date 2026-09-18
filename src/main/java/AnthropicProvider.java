package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class AnthropicProvider implements AIProvider {

    private final WebClient webClient;

    public AnthropicProvider(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public String generate(String message) {

        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "ANTHROPIC_API_KEY is not configured.";
        }

        String model = System.getenv()
                .getOrDefault("ANTHROPIC_MODEL", "claude-sonnet-4-5");

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                }
        );

        return webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
