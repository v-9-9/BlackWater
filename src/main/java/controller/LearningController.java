package controller;

import org.springframework.web.bind.annotation.*;
import service.LearningEngine;

@RestController
@RequestMapping("/api/learn")
@CrossOrigin(origins = "*")
public class LearningController {

    private final LearningEngine learningEngine;

    public LearningController(LearningEngine learningEngine) {
        this.learningEngine = learningEngine;
    }

    @PostMapping
    public String learn(@RequestBody String topic) {
        return learningEngine.learn(topic);
    }
}
