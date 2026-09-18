package service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
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

        String cleanQuery = query.trim();

        Map<String, String> sources =
                new LinkedHashMap<>();

        collectWikipedia(cleanQuery, sources);
        collectDuckDuckGo(cleanQuery, sources);

        if (sources.isEmpty()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        for (Map.Entry<String, String> entry
                : sources.entrySet()) {

            result.append("Source: ")
                    .append(entry.getKey())
                    .append(System.lineSeparator());

            result.append(entry.getValue())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        return result.toString().trim();
    }

    private void collectWikipedia(
            String query,
            Map<String, String> sources
    ) {

        try {

            String response = webClient.get()
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

            if (response != null
                    && !response.isBlank()) {

                sources.put(
                        "Wikipedia",
                        response
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void collectDuckDuckGo(
            String query,
            Map<String, String> sources
    ) {

        try {

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.duckduckgo.com")
                            .path("/")
                            .queryParam(
                                    "q",
                                    query
                            )
                            .queryParam(
                                    "format",
                                    "json"
                            )
                            .queryParam(
                                    "no_html",
                                    "1"
                            )
                            .queryParam(
                                    "skip_disambig",
                                    "1"
                            )
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null
                    && !response.isBlank()) {

                sources.put(
                        "DuckDuckGo",
                        response
                );
            }

        } catch (Exception ignored) {
        }
    }
}
