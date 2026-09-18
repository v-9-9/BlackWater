package controller;

import org.springframework.web.bind.annotation.*;
import service.KnowledgeService;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/search")
    public List<String> search(@RequestParam String q) {
        return knowledgeService.search(q);
    }

    @GetMapping("/count")
    public long count() {
        return knowledgeService.count();
    }
}
