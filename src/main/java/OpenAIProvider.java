package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class OpenAIProvider implements AIProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAIProvider(
            WebClient.Builder builder,
            ObjectMapper objectMapper
    ) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
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
                System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "OPENAI_API_KEY is not configured.";
        }

        String model = getModel(mode);

        Map<String, Object> body = Map.of(
                "model",
                model,
                "input",
                message
        );

        try {

            String rawResponse =
                    webClient.post()
                            .uri(
                                    "https://api.openai.com/v1/responses"
                            )
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

            return parseResponse(rawResponse);

        } catch (Exception e) {

            return "OpenAI request failed: "
                    + e.getMessage();
        }
    }

    private String parseResponse(
            String response
    ) {

        if (response == null
                || response.isBlank()) {

            return "No response was generated.";
        }

        try {

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode outputText =
                    root.path("output_text");

            if (!outputText.isMissingNode()
                    && !outputText.isNull()
                    && !outputText.asText().isBlank()) {

                return outputText.asText();
            }

            JsonNode output =
                    root.path("output");

            if (output.isArray()) {

                StringBuilder text =
                        new StringBuilder();

                for (JsonNode item : output) {

                    JsonNode content =
                            item.path("content");

                    if (!content.isArray()) {
                        continue;
                    }

                    for (JsonNode part : content) {

                        JsonNode textNode =
                                part.path("text");

                        if (!textNode.isMissingNode()
                                && !textNode.isNull()
                                && !textNode.asText().isBlank()) {

                            text.append(
                                    textNode.asText()
                            );
                        }
                    }
                }

                if (!text.isEmpty()) {
                    return text.toString();
                }
            }

            return response;

        } catch (Exception e) {

            return response;
        }
    }

    private String getModel(String mode) {

        String defaultModel =
                System.getenv()
                        .getOrDefault(
                                "OPENAI_MODEL",
                                "gpt-5.6-luna"
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
                                    "gpt-5.6-terra"
                            );

            case "prime" ->
                    System.getenv()
                            .getOrDefault(
                                    "OPENAI_PRIME_MODEL",
                                    "gpt-5.6-sol"
                            );

            case "swift", "fast" ->
                    defaultModel;

            case "powerful" ->
                    System.getenv()
                            .getOrDefault(
                                    "OPENAI_PRIME_MODEL",
                                    "gpt-5.6-sol"
                            );

            default ->
                    defaultModel;
        };
    }
}
