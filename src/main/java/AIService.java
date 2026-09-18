import org.springframework.stereotype.Service;

@Service
public class AIService {

    public String generate(String message) {
        return "Blackwater received: " + message;
    }
}
