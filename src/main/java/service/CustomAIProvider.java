package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class CustomAIProvider implements AIProvider {

    private final WebClient webClient;

    public CustomAIProvider(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public String generate(String message) {

        String apiKey = System.getenv("CUSTOM_AI_API_KEY");
        String baseUrl = System.getenv("CUSTOM_AI_BASE_URL");
        String model = System.getenv("CUSTOM_AI_MODEL");

        if (baseUrl == null || baseUrl.isBlank()) {
            return "CUSTOM_AI_BASE_URL is not configured.";
        }

        if (model == null || model.isBlank()) {
            model = "default";
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", new Object[]{
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                }
        );

        var request = webClient.post()
                .uri(baseUrl)
                .header("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        return request
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
