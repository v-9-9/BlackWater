package controller;

import org.springframework.web.bind.annotation.*;
import service.EvolutionEngine;

@RestController
@RequestMapping("/api/evolution")
@CrossOrigin(origins = "*")
public class EvolutionController {

    private final EvolutionEngine evolutionEngine;

    public EvolutionController(EvolutionEngine evolutionEngine) {
        this.evolutionEngine = evolutionEngine;
    }

    @GetMapping
    public EvolutionEngine.EvolutionState status() {
        return evolutionEngine.getState();
    }

    @PostMapping("/start")
    public EvolutionEngine.EvolutionState start() {
        evolutionEngine.start();
        return evolutionEngine.getState();
    }

    @PostMapping("/pause")
    public EvolutionEngine.EvolutionState pause() {
        evolutionEngine.pause();
        return evolutionEngine.getState();
    }

    @PostMapping("/resume")
    public EvolutionEngine.EvolutionState resume() {
        evolutionEngine.resume();
        return evolutionEngine.getState();
    }

    @PostMapping("/cycle")
    public EvolutionEngine.EvolutionCycle evolveOnce() {
        return evolutionEngine.evolveOnce();
    }

    @PostMapping("/domain")
    public EvolutionEngine.EvolutionState setDomain(
            @RequestParam String name,
            @RequestParam boolean enabled
    ) {
        evolutionEngine.setDomainEnabled(name, enabled);
        return evolutionEngine.getState();
    }

    @PostMapping("/priority")
    public EvolutionEngine.EvolutionState setPriority(
            @RequestParam String name,
            @RequestParam int value
    ) {
        evolutionEngine.setPriority(name, value);
        return evolutionEngine.getState();
    }
}
