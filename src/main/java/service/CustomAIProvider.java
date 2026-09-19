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
public class CustomAIProvider implements AIProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CustomAIProvider(ObjectMapper objectMapper) {
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
        String baseUrl =
                System.getenv("CUSTOM_AI_BASE_URL");

        String apiKey =
                System.getenv("CUSTOM_AI_API_KEY");

        String model =
                getEnvironment(
                        "CUSTOM_AI_MODEL",
                        "default"
                );

        if (baseUrl == null || baseUrl.isBlank()) {
            return "CUSTOM_AI_BASE_URL is not configured.";
        }

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

                    String mediaType =
                            image.mediaType();

                    if (mediaType == null
                            || mediaType.isBlank()) {
                        mediaType = "image/jpeg";
                    }

                    Map<String, Object> imageUrl =
                            new HashMap<>();

                    imageUrl.put(
                            "url",
                            "data:"
                                    + mediaType
                                    + ";base64,"
                                    + image.base64Data()
                    );

                    Map<String, Object> imagePart =
                            new HashMap<>();

                    imagePart.put(
                            "type",
                            "image_url"
                    );

                    imagePart.put(
                            "image_url",
                            imageUrl
                    );

                    content.add(imagePart);
                }
            }

            Map<String, Object> textPart =
                    new HashMap<>();

            textPart.put(
                    "type",
                    "text"
            );

            textPart.put(
                    "text",
                    message == null
                            ? ""
                            : message
            );

            content.add(textPart);

            Map<String, Object> userMessage =
                    new HashMap<>();

            userMessage.put(
                    "role",
                    "user"
            );

            userMessage.put(
                    "content",
                    content
            );

            Map<String, Object> body =
                    new HashMap<>();

            body.put(
                    "model",
                    model
            );

            body.put(
                    "messages",
                    List.of(userMessage)
            );

            String json =
                    objectMapper.writeValueAsString(
                            body
                    );

            String endpoint =
                    normalizeEndpoint(baseUrl);

            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(endpoint)
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            );

            if (apiKey != null
                    && !apiKey.isBlank()) {

                requestBuilder.header(
                        "Authorization",
                        "Bearer " + apiKey
                );
            }

            HttpRequest request =
                    requestBuilder
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                return "Custom AI request failed: "
                        + response.statusCode()
                        + " "
                        + response.body();
            }

            return extractResponseText(
                    response.body()
            );

        } catch (Exception e) {
            return "Custom AI request failed: "
                    + e.getMessage();
        }
    }

    private String extractResponseText(
            String responseBody
    ) {
        try {
            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode choices =
                    root.path("choices");

            if (choices.isArray()
                    && choices.size() > 0) {

                JsonNode message =
                        choices
                                .get(0)
                                .path("message");

                JsonNode content =
                        message.path("content");

                if (content.isTextual()) {
                    return content.asText();
                }

                if (content.isArray()) {

                    StringBuilder result =
                            new StringBuilder();

                    for (JsonNode item : content) {

                        JsonNode text =
                                item.path("text");

                        if (text.isTextual()
                                && !text.asText().isBlank()) {

                            if (result.length() > 0) {
                                result.append("\n");
                            }

                            result.append(
                                    text.asText()
                            );
                        }
                    }

                    if (result.length() > 0) {
                        return result.toString();
                    }
                }
            }

            JsonNode outputText =
                    root.path("output_text");

            if (outputText.isTextual()
                    && !outputText.asText().isBlank()) {

                return outputText.asText();
            }

            return responseBody;

        } catch (Exception e) {
            return responseBody;
        }
    }

    private String normalizeEndpoint(
            String baseUrl
    ) {
        String endpoint =
                baseUrl.trim();

        while (endpoint.endsWith("/")) {
            endpoint =
                    endpoint.substring(
                            0,
                            endpoint.length() - 1
                    );
        }

        if (!endpoint.endsWith("/chat/completions")) {
            endpoint += "/chat/completions";
        }

        return endpoint;
    }

    private String getEnvironment(
            String name,
            String fallback
    ) {
        String value =
                System.getenv(name);

        if (value == null
                || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}
