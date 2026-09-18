package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GeminiProvider implements AIProvider {

    private final WebClient webClient;
    private final AIResponseParser responseParser;

    public GeminiProvider(
            WebClient.Builder builder,
            AIResponseParser responseParser
    ) {
        this.webClient = builder.build();
        this.responseParser = responseParser;
    }

    @Override
    public String generate(String message) {
        return generate(message, "swift");
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

        try {

            String rawResponse = webClient.post()
                    .uri(url)
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return responseParser.parseGemini(rawResponse);

        } catch (Exception e) {

            return "Gemini request failed: "
                    + e.getMessage();
        }
    }

    private String getModel(String mode) {

        String defaultModel = System.getenv()
                .getOrDefault(
                        "GEMINI_MODEL",
                        "gemini-2.5-flash"
                );

        String selectedMode =
                mode == null
                        ? "swift"
                        : mode.toLowerCase().trim();

        return switch (selectedMode) {

            case "deep" ->
                    System.getenv()
                            .getOrDefault(
                                    "GEMINI_DEEP_MODEL",
                                    "gemini-2.5-pro"
                            );

            case "prime" ->
                    System.getenv()
                            .getOrDefault(
                                    "GEMINI_PRIME_MODEL",
                                    "gemini-2.5-pro"
                            );

            case "swift", "fast" ->
                    defaultModel;

            case "powerful" ->
                    System.getenv()
                            .getOrDefault(
                                    "GEMINI_PRIME_MODEL",
                                    "gemini-2.5-pro"
                            );

            default ->
                    defaultModel;
        };
    }
}
