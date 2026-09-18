package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class OpenAIProvider implements AIProvider {

    private final WebClient webClient;
    private final AIResponseParser responseParser;

    public OpenAIProvider(
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

        try {

            String rawResponse = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header(
                            "Authorization",
                            "Bearer " + apiKey
                    )
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return responseParser.parseOpenAI(rawResponse);

        } catch (Exception e) {

            return "OpenAI request failed: "
                    + e.getMessage();
        }
    }

    private String getModel(String mode) {

        String defaultModel = System.getenv()
                .getOrDefault(
                        "OPENAI_MODEL",
                        "gpt-4o-mini"
                );

        String selectedMode =
                mode == null
                        ? "swift"
                        : mode.toLowerCase().trim();

        return switch (selectedMode) {

            case "deep" ->
                    System.getenv()
                            .getOrDefault(
                                    "OPENAI_DEEP_MODEL",
                                    defaultModel
                            );

            case "prime" ->
                    System.getenv()
                            .getOrDefault(
                                    "OPENAI_PRIME_MODEL",
                                    defaultModel
                            );

            case "swift", "fast" ->
                    defaultModel;

            case "powerful" ->
                    System.getenv()
                            .getOrDefault(
                                    "OPENAI_PRIME_MODEL",
                                    defaultModel
                            );

            default ->
                    defaultModel;
        };
    }
}
