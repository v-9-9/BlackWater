package controller;

import org.springframework.web.bind.annotation.*;
import service.KnowledgeEntry;
import service.LearningEngine;

import java.util.List;

@RestController
@RequestMapping("/api/learn")
@CrossOrigin(origins = "*")
public class LearningController {

    private final LearningEngine learningEngine;

    public LearningController(LearningEngine learningEngine) {
        this.learningEngine = learningEngine;
    }

    @PostMapping
    public List<KnowledgeEntry> learn(@RequestBody String topic) {
        return learningEngine.learn(topic);
    }
}
