package controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    @GetMapping
    public Map<String, String> provider() {

        String provider = System.getenv()
                .getOrDefault("AI_PROVIDER", "openai");

        return Map.of(
                "provider", provider,
                "status", "active"
        );
    }
}
