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
public class OpenAIProvider implements AIProvider {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAIProvider(ObjectMapper objectMapper) {
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
                System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "OPENAI_API_KEY is not configured.";
        }

        String model =
                getEnvironment(
                        "OPENAI_MODEL",
                        DEFAULT_MODEL
                );

        try {
            Map<String, Object> body =
                    new HashMap<>();

            body.put("model", model);

            List<Map<String, Object>> content =
                    new ArrayList<>();

            Map<String, Object> text =
                    new HashMap<>();

            text.put("type", "input_text");
            text.put(
                    "text",
                    message == null ? "" : message
            );

            content.add(text);

            if (images != null) {
                for (
                        AIProvider.ImageInput image
                        : images
                ) {
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

                    Map<String, Object> imagePart =
                            new HashMap<>();

                    imagePart.put(
                            "type",
                            "input_image"
                    );

                    imagePart.put(
                            "image_url",
                            "data:"
                                    + mediaType
                                    + ";base64,"
                                    + image.base64Data()
                    );

                    content.add(imagePart);
                }
            }

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

            body.put(
                    "input",
                    List.of(userMessage)
            );

            String json =
                    objectMapper.writeValueAsString(
                            body
                    );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://api.openai.com/v1/responses"
                                    )
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + apiKey
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
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                return "OpenAI request failed: "
                        + response.statusCode()
                        + " "
                        + response.body();
            }

            return extractResponseText(
                    response.body()
            );

        } catch (Exception e) {
            return "OpenAI request failed: "
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

            JsonNode outputText =
                    root.path("output_text");

            if (outputText.isTextual()
                    && !outputText.asText().isBlank()) {
                return outputText.asText();
            }

            JsonNode output =
                    root.path("output");

            if (output.isArray()) {
                StringBuilder result =
                        new StringBuilder();

                for (JsonNode item : output) {
                    JsonNode content =
                            item.path("content");

                    if (!content.isArray()) {
                        continue;
                    }

                    for (JsonNode part : content) {
                        JsonNode text =
                                part.path("text");

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
                }

                if (result.length() > 0) {
                    return result.toString();
                }
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

        if (value == null
                || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}
