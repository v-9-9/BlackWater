package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AIResponseParser {

    private final ObjectMapper objectMapper;

    public AIResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String parseOpenAI(String response) {

        if (response == null || response.isBlank()) {
            return "No response was generated.";
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode content = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (!content.isMissingNode()
                    && !content.isNull()
                    && !content.asText().isBlank()) {

                return content.asText();
            }

            return response;

        } catch (Exception e) {
            return response;
        }
    }

    public String parseGemini(String response) {

        if (response == null || response.isBlank()) {
            return "No response was generated.";
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode parts = root
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts");

            if (parts.isArray()) {

                StringBuilder text = new StringBuilder();

                for (JsonNode part : parts) {

                    JsonNode textNode = part.path("text");

                    if (!textNode.isMissingNode()
                            && !textNode.isNull()) {

                        String value = textNode.asText();

                        if (!value.isBlank()) {
                            text.append(value);
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

    public String parseAnthropic(String response) {

        if (response == null || response.isBlank()) {
            return "No response was generated.";
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode content = root
                    .path("content");

            if (content.isArray()) {

                StringBuilder text = new StringBuilder();

                for (JsonNode item : content) {

                    JsonNode textNode = item.path("text");

                    if (!textNode.isMissingNode()
                            && !textNode.isNull()) {

                        String value = textNode.asText();

                        if (!value.isBlank()) {
                            text.append(value);
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

    public String parseCustom(String response) {

        if (response == null || response.isBlank()) {
            return "No response was generated.";
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode openAIContent = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (!openAIContent.isMissingNode()
                    && !openAIContent.isNull()
                    && !openAIContent.asText().isBlank()) {

                return openAIContent.asText();
            }

            JsonNode responseText = root.path("response");

            if (!responseText.isMissingNode()
                    && !responseText.isNull()
                    && !responseText.asText().isBlank()) {

                return responseText.asText();
            }

            JsonNode text = root.path("text");

            if (!text.isMissingNode()
                    && !text.isNull()
                    && !text.asText().isBlank()) {

                return text.asText();
            }

            return response;

        } catch (Exception e) {
            return response;
        }
    }
}
