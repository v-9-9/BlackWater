package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class WebKnowledgeCollector {

    private final WebClient webClient;

    public WebKnowledgeCollector(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public String collect(String query) {

        if (query == null || query.isBlank()) {
            return "";
        }

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("en.wikipedia.org")
                            .path("/w/api.php")
                            .queryParam("action", "query")
                            .queryParam("prop", "extracts")
                            .queryParam("explaintext", "true")
                            .queryParam("exintro", "true")
                            .queryParam("format", "json")
                            .queryParam("titles", query)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            return "Knowledge collection failed: " + e.getMessage();
        }
    }
}
