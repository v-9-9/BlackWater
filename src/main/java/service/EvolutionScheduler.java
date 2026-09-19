package service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EvolutionScheduler {

    private final EvolutionEngine evolutionEngine;

    public EvolutionScheduler(
            EvolutionEngine evolutionEngine
    ) {
        this.evolutionEngine =
                evolutionEngine;
    }

    @Scheduled(
            fixedDelayString = "${blackwater.evolution.interval-ms:3600000}"
    )
    public void runEvolutionCycle() {

        try {
            evolutionEngine.evolveOnce();
        } catch (Exception ignored) {
        }
    }

    public EvolutionEngine.EvolutionState getState() {
        return evolutionEngine.getState();
    }

    public EvolutionEngine.EvolutionCycle evolveNow() {
        return evolutionEngine.evolveOnce();
    }

    public void start() {
        evolutionEngine.start();
    }

    public void pause() {
        evolutionEngine.pause();
    }

    public void resume() {
        evolutionEngine.resume();
    }

    public void setDomainEnabled(
            String domain,
            boolean enabled
    ) {
        evolutionEngine.setDomainEnabled(
                domain,
                enabled
        );
    }

    public void setPriority(
            String domain,
            int priority
    ) {
        evolutionEngine.setPriority(
                domain,
                priority
        );
    }
}
