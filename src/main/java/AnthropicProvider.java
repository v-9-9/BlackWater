package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnthropicProvider implements AIProvider {

    private static final String DEFAULT_MODEL = "claude-sonnet-5";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AnthropicProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String generate(String message) {
        return generate(message, List.of());
    }

    @Override
    public String generate(
            String message,
            List<AIProvider.ImageInput> images
    ) {
        String apiKey =
                System.getenv("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "ANTHROPIC_API_KEY is not configured.";
        }

        String model =
                getEnvironment(
                        "ANTHROPIC_MODEL",
                        DEFAULT_MODEL
                );

        try {
            List<Map<String, Object>> content =
                    new ArrayList<>();

            if (images != null) {
                for (AIProvider.ImageInput image : images) {
                    if (image == null
                            || image.base64Data() == null
                            || image.base64Data().isBlank()) {
                        continue;
                    }

                    String mediaType = image.mediaType();

                    if (mediaType == null
                            || mediaType.isBlank()) {
                        mediaType = "image/jpeg";
                    }

                    Map<String, Object> source =
                            new HashMap<>();

                    source.put("type", "base64");
                    source.put("media_type", mediaType);
                    source.put("data", image.base64Data());

                    Map<String, Object> imageBlock =
                            new HashMap<>();

                    imageBlock.put("type", "image");
                    imageBlock.put("source", source);

                    content.add(imageBlock);
                }
            }

            Map<String, Object> textBlock =
                    new HashMap<>();

            textBlock.put("type", "text");
            textBlock.put(
                    "text",
                    message == null ? "" : message
            );

            content.add(textBlock);

            Map<String, Object> userMessage =
                    new HashMap<>();

            userMessage.put("role", "user");
            userMessage.put("content", content);

            Map<String, Object> body =
                    new HashMap<>();

            body.put("model", model);
            body.put("max_tokens", 4096);
            body.put(
                    "messages",
                    List.of(userMessage)
            );

            String json =
                    objectMapper.writeValueAsString(body);

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://api.anthropic.com/v1/messages"
                                    )
                            )
                            .header(
                                    "x-api-key",
                                    apiKey
                            )
                            .header(
                                    "anthropic-version",
                                    "2023-06-01"
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                return "Anthropic request failed: "
                        + response.statusCode()
                        + " "
                        + response.body();
            }

            return extractResponseText(
                    response.body()
            );

        } catch (Exception e) {
            return "Anthropic request failed: "
                    + e.getMessage();
        }
    }

    private String extractResponseText(
            String responseBody
    ) {
        try {
            JsonNode root =
                    objectMapper.readTree(responseBody);

            JsonNode content =
                    root.path("content");

            StringBuilder result =
                    new StringBuilder();

            if (content.isArray()) {
                for (JsonNode block : content) {
                    if (!"text".equals(
                            block.path("type").asText()
                    )) {
                        continue;
                    }

                    String text =
                            block.path("text").asText("");

                    if (text.isBlank()) {
                        continue;
                    }

                    if (result.length() > 0) {
                        result.append("\n");
                    }

                    result.append(text);
                }
            }

            if (result.length() > 0) {
                return result.toString();
            }

            return responseBody;

        } catch (Exception e) {
            return responseBody;
        }
    }

    private String getEnvironment(
            String name,
            String fallback
    ) {
        String value =
                System.getenv(name);

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}
