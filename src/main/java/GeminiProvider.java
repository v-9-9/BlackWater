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
public class GeminiProvider implements AIProvider {

    private static final String DEFAULT_MODEL =
            "gemini-2.5-flash";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiProvider(ObjectMapper objectMapper) {
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
                System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "GEMINI_API_KEY is not configured.";
        }

        String model =
                getEnvironment(
                        "GEMINI_MODEL",
                        DEFAULT_MODEL
                );

        try {
            List<Map<String, Object>> parts =
                    new ArrayList<>();

            Map<String, Object> textPart =
                    new HashMap<>();

            textPart.put(
                    "text",
                    message == null ? "" : message
            );

            parts.add(textPart);

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

                    String mimeType =
                            image.mediaType();

                    if (mimeType == null
                            || mimeType.isBlank()) {
                        mimeType = "image/jpeg";
                    }

                    Map<String, Object> inlineData =
                            new HashMap<>();

                    inlineData.put(
                            "mime_type",
                            mimeType
                    );

                    inlineData.put(
                            "data",
                            image.base64Data()
                    );

                    Map<String, Object> imagePart =
                            new HashMap<>();

                    imagePart.put(
                            "inline_data",
                            inlineData
                    );

                    parts.add(imagePart);
                }
            }

            Map<String, Object> content =
                    new HashMap<>();

            content.put(
                    "role",
                    "user"
            );

            content.put(
                    "parts",
                    parts
            );

            Map<String, Object> body =
                    new HashMap<>();

            body.put(
                    "contents",
                    List.of(content)
            );

            String json =
                    objectMapper.writeValueAsString(
                            body
                    );

            String endpoint =
                    "https://generativelanguage.googleapis.com/"
                            + "v1beta/models/"
                            + model
                            + ":generateContent";

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .header(
                                    "x-goog-api-key",
                                    apiKey
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

                return "Gemini request failed: "
                        + response.statusCode()
                        + " "
                        + response.body();
            }

            return extractResponseText(
                    response.body()
            );

        } catch (Exception e) {
            return "Gemini request failed: "
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

            StringBuilder result =
                    new StringBuilder();

            JsonNode candidates =
                    root.path("candidates");

            if (candidates.isArray()) {

                for (JsonNode candidate : candidates) {

                    JsonNode parts =
                            candidate
                                    .path("content")
                                    .path("parts");

                    if (!parts.isArray()) {
                        continue;
                    }

                    for (JsonNode part : parts) {

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

        if (value == null
                || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}
