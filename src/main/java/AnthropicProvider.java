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
        return generate(message, "swift");
    }

    public String generate(String message, String mode) {

        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "ANTHROPIC_API_KEY is not configured.";
        }

        String model = getModel(mode);

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
                .header(
                        "anthropic-version",
                        "2023-06-01"
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String getModel(String mode) {

        String defaultModel = System.getenv()
                .getOrDefault(
                        "ANTHROPIC_MODEL",
                        "claude-sonnet-5"
                );

        String selectedMode =
                mode == null
                        ? "swift"
                        : mode.toLowerCase().trim();

        return switch (selectedMode) {

            case "deep" ->
                    System.getenv()
                            .getOrDefault(
                                    "ANTHROPIC_DEEP_MODEL",
                                    "claude-opus-5"
                            );

            case "prime" ->
                    System.getenv()
                            .getOrDefault(
                                    "ANTHROPIC_PRIME_MODEL",
                                    "claude-fable-5"
                            );

            case "swift", "fast" ->
                    defaultModel;

            case "powerful" ->
                    System.getenv()
                            .getOrDefault(
                                    "ANTHROPIC_PRIME_MODEL",
                                    "claude-fable-5"
                            );

            default ->
                    defaultModel;
        };
    }
}
