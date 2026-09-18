package service;

import org.springframework.stereotype.Service;

@Service
public class AIService {

    public String generate(String message) {

        return "AI connection is ready: " + message;
    }
}
