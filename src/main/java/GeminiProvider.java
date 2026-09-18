package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GeminiProvider implements AIProvider {

    private final WebClient webClient;

    public GeminiProvider(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public String generate(String message) {
        return generate(message, "fast");
    }

    public String generate(String message, String mode) {

        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "GEMINI_API_KEY is not configured.";
        }

        String model = getModel(mode);

        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of(
                                "parts", new Object[]{
                                        Map.of("text", message)
                                }
                        )
                }
        );

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model
                        + ":generateContent?key="
                        + apiKey;

        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String getModel(String mode) {

        String defaultModel = System.getenv()
                .getOrDefault("GEMINI_MODEL", "gemini-2.5-flash");

        return switch (mode == null ? "fast" : mode.toLowerCase()) {
            case "deep" -> System.getenv()
                    .getOrDefault("GEMINI_DEEP_MODEL", defaultModel);

            case "powerful" -> System.getenv()
                    .getOrDefault("GEMINI_POWERFUL_MODEL", defaultModel);

            default -> defaultModel;
        };
    }
}
