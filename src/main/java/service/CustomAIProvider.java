package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class CustomAIProvider implements AIProvider {

    private final WebClient webClient;
    private final AIResponseParser responseParser;

    public CustomAIProvider(
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

    public String generate(
            String message,
            String mode
    ) {

        String apiKey =
                System.getenv("CUSTOM_AI_API_KEY");

        String baseUrl =
                System.getenv("CUSTOM_AI_BASE_URL");

        if (baseUrl == null || baseUrl.isBlank()) {
            return "CUSTOM_AI_BASE_URL is not configured.";
        }

        String model = getModel(mode);

        Map<String, Object> body = Map.of(
                "model",
                model,
                "messages",
                new Object[]{
                        Map.of(
                                "role",
                                "user",
                                "content",
                                message
                        )
                }
        );

        try {

            var request =
                    webClient.post()
                            .uri(baseUrl)
                            .header(
                                    "Content-Type",
                                    "application/json"
                            );

            if (apiKey != null
                    && !apiKey.isBlank()) {

                request.header(
                        "Authorization",
                        "Bearer " + apiKey
                );
            }

            String rawResponse =
                    request
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            return responseParser.parseCustom(
                    rawResponse
            );

        } catch (Exception e) {

            return "Custom AI request failed: "
                    + e.getMessage();
        }
    }

    private String getModel(String mode) {

        String defaultModel =
                System.getenv()
                        .getOrDefault(
                                "CUSTOM_AI_MODEL",
                                "default"
                        );

        String selectedMode =
                mode == null
                        ? "swift"
                        : mode.toLowerCase().trim();

        return switch (selectedMode) {

            case "deep" ->
                    System.getenv()
                            .getOrDefault(
                                    "CUSTOM_AI_DEEP_MODEL",
                                    defaultModel
                            );

            case "prime" ->
                    System.getenv()
                            .getOrDefault(
                                    "CUSTOM_AI_PRIME_MODEL",
                                    defaultModel
                            );

            case "swift", "fast" ->
                    defaultModel;

            case "powerful" ->
                    System.getenv()
                            .getOrDefault(
                                    "CUSTOM_AI_PRIME_MODEL",
                                    defaultModel
                            );

            default ->
                    defaultModel;
        };
    }
}
