package controller;

import org.springframework.web.bind.annotation.*;
import service.MemoryService;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
@CrossOrigin(origins = "*")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(
            MemoryService memoryService
    ) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<String> getMemories() {

        return memoryService.getMemories();
    }

    @PostMapping
    public String remember(
            @RequestBody String information
    ) {

        if (information == null
                || information.isBlank()) {

            return "Memory is empty.";
        }

        memoryService.remember(information);

        return "Memory saved.";
    }

    @DeleteMapping
    public String clearMemory() {

        memoryService.clear();

        return "Memory cleared.";
    }
}
