package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EvolutionEngine {

    private static final Path STATE_FILE =
            Path.of("blackwater-evolution.txt");

    private static final Path LOG_FILE =
            Path.of("blackwater-evolution-log.txt");

    private final ImprovementEngine improvementEngine;
    private final BenchmarkEngine benchmarkEngine;

    private final Map<Domain, DomainState> states =
            new EnumMap<>(Domain.class);

    private boolean running = true;

    public EvolutionEngine(
            ImprovementEngine improvementEngine,
            BenchmarkEngine benchmarkEngine
    ) {

        this.improvementEngine =
                improvementEngine;

        this.benchmarkEngine =
                benchmarkEngine;

        initializeDefaults();
        loadState();
    }

    public synchronized EvolutionState getState() {

        List<DomainState> result =
                new ArrayList<>();

        for (Domain domain : Domain.values()) {

            DomainState state =
                    states.get(domain);

            result.add(
                    new DomainState(
                            state.domain(),
                            state.enabled(),
                            state.priority(),
                            state.power(),
                            state.cycles(),
                            state.lastBenchmark()
                    )
            );
        }

        return new EvolutionState(
                running,
                result
        );
    }

    public synchronized EvolutionCycle evolveOnce() {

        if (!running) {

            return new EvolutionCycle(
                    false,
                    "",
                    0,
                    0,
                    0,
                    "Evolution is paused."
            );
        }

        Domain target =
                selectNextDomain();

        DomainState state =
                states.get(target);

        int before =
                safeBenchmark(target);

        ImprovementEngine.ImprovementResult result;

        try {

            result =
                    improvementEngine.improveDomain(
                            target.name().toLowerCase(
                                    Locale.ROOT
                            )
                    );

        } catch (Exception e) {

            state.cycles(
                    state.cycles() + 1
            );

            state.lastBenchmark(before);

            saveState();

            return new EvolutionCycle(
                    true,
                    target.name(),
                    before,
                    before,
                    0,
                    "Improvement cycle failed."
            );
        }

        int after =
                Math.max(
                        0,
                        result.afterScore()
                );

        int improvement =
                after - before;

        state.cycles(
                state.cycles() + 1
        );

        state.lastBenchmark(after);

        if (improvement > 0) {

            int gain =
                    calculatePowerGain(
                            state,
                            improvement
                    );

            state.power(
                    state.power() + gain
            );
        }

        saveState();

        logCycle(
                target,
                before,
                after,
                improvement,
                result.successful()
        );

        return new EvolutionCycle(
                true,
                target.name(),
                before,
                after,
                improvement,
                result.message()
        );
    }

    public synchronized void start() {
        running = true;
        saveState();
    }

    public synchronized void pause() {
        running = false;
        saveState();
    }

    public synchronized void resume() {
        running = true;
        saveState();
    }

    public synchronized void setDomainEnabled(
            String domain,
            boolean enabled
    ) {

        Domain parsed =
                parseDomain(domain);

        if (parsed == null) {
            return;
        }

        states.get(parsed).enabled(
                enabled
        );

        saveState();
    }

    public synchronized void setPriority(
            String domain,
            int priority
    ) {

        Domain parsed =
                parseDomain(domain);

        if (parsed == null) {
            return;
        }

        states.get(parsed).priority(
                Math.max(
                        0,
                        priority
                )
        );

        saveState();
    }

    private Domain selectNextDomain() {

        return states.values()
                .stream()
                .filter(
                        DomainState::enabled
                )
                .min(
                        Comparator
                                .comparingInt(
                                        DomainState::priority
                                )
                                .reversed()
                                .thenComparingInt(
                                        DomainState::cycles
                                )
                                .thenComparingInt(
                                        DomainState::power
                                )
                )
                .map(
                        DomainState::domain
                )
                .orElse(
                        Domain.KNOWLEDGE
                );
    }

    private int calculatePowerGain(
            DomainState state,
            int improvement
    ) {

        int gain =
                Math.max(
                        1,
                        improvement
                );

        if (state.priority() >= 90) {
            gain += 2;
        } else if (state.priority() >= 70) {
            gain += 1;
        }

        return gain;
    }

    private int safeBenchmark(
            Domain domain
    ) {

        try {

            return benchmarkEngine
                    .run(
                            domain.name()
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                    )
                    .score();

        } catch (Exception ignored) {

            return 0;
        }
    }

    private Domain parseDomain(
            String value
    ) {

        if (value == null
                || value.isBlank()) {
            return null;
        }

        try {

            return Domain.valueOf(
                    value.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (Exception ignored) {

            return null;
        }
    }

    private void initializeDefaults() {

        states.put(
                Domain.KNOWLEDGE,
                new DomainState(
                        Domain.KNOWLEDGE,
                        true,
                        60,
                        0,
                        0,
                        0
                )
        );

        states.put(
                Domain.REASONING,
                new DomainState(
                        Domain.REASONING,
                        true,
                        80,
                        0,
                        0,
                        0
                )
        );

        states.put(
                Domain.RESEARCH,
                new DomainState(
                        Domain.RESEARCH,
                        true,
                        70,
                        0,
                        0,
                        0
                )
        );

        states.put(
                Domain.CODING,
                new DomainState(
                        Domain.CODING,
                        true,
                        100,
                        0,
                        0,
                        0
                )
        );

        states.put(
                Domain.MEMORY,
                new DomainState(
                        Domain.MEMORY,
                        true,
                        50,
                        0,
                        0,
                        0
                )
        );
    }

    private void loadState() {

        if (!Files.exists(STATE_FILE)) {
            return;
        }

        try {

            List<String> lines =
                    Files.readAllLines(
                            STATE_FILE,
                            StandardCharsets.UTF_8
                    );

            for (String line : lines) {

                String[] parts =
                        line.split(
                                "\\|",
                                -1
                        );

                if (parts.length < 7) {
                    continue;
                }

                if ("RUNNING".equals(parts[0])) {

                    running =
                            Boolean.parseBoolean(
                                    parts[1]
                            );

                    continue;
                }

                Domain domain;

                try {

                    domain =
                            Domain.valueOf(
                                    parts[0]
                                            .toUpperCase(
                                                    Locale.ROOT
                                            )
                            );

                } catch (Exception ignored) {

                    continue;
                }

                DomainState state =
                        states.get(domain);

                if (state == null) {
                    continue;
                }

                state.enabled(
                        Boolean.parseBoolean(
                                parts[1]
                        )
                );

                state.priority(
                        parseInt(
                                parts[2],
                                state.priority()
                        )
                );

                state.power(
                        parseInt(
                                parts[3],
                                state.power()
                        )
                );

                state.cycles(
                        parseInt(
                                parts[4],
                                state.cycles()
                        )
                );

                state.lastBenchmark(
                        parseInt(
                                parts[5],
                                state.lastBenchmark()
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void saveState() {

        List<String> lines =
                new ArrayList<>();

        lines.add(
                "RUNNING|"
                        + running
        );

        for (Domain domain :
                Domain.values()) {

            DomainState state =
                    states.get(domain);

            lines.add(
                    domain.name()
                            + "|"
                            + state.enabled()
                            + "|"
                            + state.priority()
                            + "|"
                            + state.power()
                            + "|"
                            + state.cycles()
                            + "|"
                            + state.lastBenchmark()
                            + "|"
                            + Instant.now()
            );
        }

        try {

            Files.write(
                    STATE_FILE,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException ignored) {
        }
    }

    private void logCycle(
            Domain domain,
            int before,
            int after,
            int improvement,
            boolean successful
    ) {

        String line =
                Instant.now()
                        + "|"
                        + domain.name()
                        + "|before="
                        + before
                        + "|after="
                        + after
                        + "|improvement="
                        + improvement
                        + "|successful="
                        + successful
                        + System.lineSeparator();

        try {

            Files.writeString(
                    LOG_FILE,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException ignored) {
        }
    }

    private int parseInt(
            String value,
            int fallback
    ) {

        try {
            return Integer.parseInt(
                    value
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public enum Domain {
        KNOWLEDGE,
        REASONING,
        RESEARCH,
        CODING,
        MEMORY
    }

    public static class DomainState {

        private Domain domain;
        private boolean enabled;
        private int priority;
        private int power;
        private int cycles;
        private int lastBenchmark;

        public DomainState(
                Domain domain,
                boolean enabled,
                int priority,
                int power,
                int cycles,
                int lastBenchmark
        ) {
            this.domain = domain;
            this.enabled = enabled;
            this.priority = priority;
            this.power = power;
            this.cycles = cycles;
            this.lastBenchmark = lastBenchmark;
        }

        public Domain domain() {
            return domain;
        }

        public boolean enabled() {
            return enabled;
        }

        public void enabled(
                boolean enabled
        ) {
            this.enabled = enabled;
        }

        public int priority() {
            return priority;
        }

        public void priority(
                int priority
        ) {
            this.priority = priority;
        }

        public int power() {
            return power;
        }

        public void power(
                int power
        ) {
            this.power = power;
        }

        public int cycles() {
            return cycles;
        }

        public void cycles(
                int cycles
        ) {
            this.cycles = cycles;
        }

        public int lastBenchmark() {
            return lastBenchmark;
        }

        public void lastBenchmark(
                int lastBenchmark
        ) {
            this.lastBenchmark = lastBenchmark;
        }
    }

    public record EvolutionState(
            boolean running,
            List<DomainState> domains
    ) {
    }

    public record EvolutionCycle(
            boolean running,
            String domain,
            int beforeScore,
            int afterScore,
            int improvement,
            String message
    ) {
    }
}
