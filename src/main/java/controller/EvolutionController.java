package controller;

import org.springframework.web.bind.annotation.*;
import service.EvolutionEngine;

@RestController
@RequestMapping("/api/evolution")
@CrossOrigin(origins = "*")
public class EvolutionController {

    private final EvolutionEngine evolutionEngine;

    public EvolutionController(
            EvolutionEngine evolutionEngine
    ) {
        this.evolutionEngine =
                evolutionEngine;
    }

    @GetMapping
    public EvolutionEngine.EvolutionSnapshot status() {

        return evolutionEngine.getStatus();
    }

    @PostMapping("/start")
    public EvolutionEngine.EvolutionSnapshot start() {

        evolutionEngine.start();

        return evolutionEngine.getStatus();
    }

    @PostMapping("/pause")
    public EvolutionEngine.EvolutionSnapshot pause() {

        evolutionEngine.pause();

        return evolutionEngine.getStatus();
    }

    @PostMapping("/resume")
    public EvolutionEngine.EvolutionSnapshot resume() {

        evolutionEngine.start();

        return evolutionEngine.getStatus();
    }

    @PostMapping("/cycle")
    public String evolveOnce() {

        return evolutionEngine.evolveOnce();
    }

    @PostMapping("/domain")
    public EvolutionEngine.EvolutionSnapshot setDomain(
            @RequestParam String name,
            @RequestParam boolean enabled
    ) {

        evolutionEngine.setDomainEnabled(
                name,
                enabled
        );

        return evolutionEngine.getStatus();
    }

    @PostMapping("/priority")
    public EvolutionEngine.EvolutionSnapshot setPriority(
            @RequestParam String name,
            @RequestParam int value
    ) {

        evolutionEngine.setPriority(
                name,
                value
        );

        return evolutionEngine.getStatus();
    }
}
