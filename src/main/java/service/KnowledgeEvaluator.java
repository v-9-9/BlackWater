package service;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeEvaluator {

    public boolean isUseful(String information) {

        if (information == null || information.isBlank()) {
            return false;
        }

        String text = information.trim();

        if (text.length() < 30) {
            return false;
        }

        String lower = text.toLowerCase();

        String[] unwanted = {
                "captcha",
                "access denied",
                "sign in",
                "log in",
                "enable javascript",
                "error 404"
        };

        for (String word : unwanted) {
            if (lower.contains(word)) {
                return false;
            }
        }

        return true;
    }
}
