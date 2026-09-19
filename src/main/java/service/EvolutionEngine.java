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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvolutionEngine {

    private static final Path EVOLUTION_FILE =
            Path.of("blackwater-evolution.txt");

    private static final Path LOG_FILE =
            Path.of("blackwater-evolution-log.txt");

    private final ImprovementEngine improvementEngine;
    private final BenchmarkEngine benchmarkEngine;

    private final Map<Domain, DomainState> domains =
            new EnumMap<>(Domain.class);

    private boolean running = false;

    private long totalPower = 0;

    private long evolutionCycles = 0;

    public EvolutionEngine(
            ImprovementEngine improvementEngine,
            BenchmarkEngine benchmarkEngine
    ) {
        this.improvementEngine = improvementEngine;
        this.benchmarkEngine = benchmarkEngine;

        initializeDomains();
        loadState();
    }

    public synchronized void start() {

        running = true;

        saveState();

        writeLog(
                "Evolution started."
        );
    }

    public synchronized void pause() {

        running = false;

        saveState();

        writeLog(
                "Evolution paused."
        );
    }

    public synchronized boolean isRunning() {

        return running;
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

        domains.get(parsed).enabled =
                enabled;

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

        domains.get(parsed).priority =
                Math.max(
                        0,
                        Math.min(
                                100,
                                priority
                        )
                );

        saveState();
    }

    public synchronized EvolutionSnapshot getStatus() {

        Map<String, DomainSnapshot> result =
                new LinkedHashMap<>();

        for (Domain domain :
                Domain.values()) {

            DomainState state =
                    domains.get(domain);

            result.put(
                    domain.name().toLowerCase(),
                    new DomainSnapshot(
                            state.enabled,
                            state.priority,
                            state.power,
                            state.cycles,
                            state.lastBenchmark
                    )
            );
        }

        return new EvolutionSnapshot(
                totalPower,
                running,
                evolutionCycles,
                result
        );
    }

    public synchronized String evolveOnce() {

        if (!running) {

            return "Evolution is paused.";
        }

        DomainState target =
                selectNextDomain();

        if (target == null) {

            return
                    "No evolution domains are enabled.";
        }

        /*
         * The improvement engine identifies the
         * weakest capability and attempts to improve it.
         */
        ImprovementEngine.ImprovementResult result;

        try {

            result =
                    improvementEngine
                            .improveWeakestDomain();

        } catch (Exception e) {

            return
                    "Evolution failed: "
                            + e.getMessage();
        }

        String domainName =
                result.domain();

        Domain targetDomain =
                parseDomain(domainName);

        if (targetDomain == null) {

            return
                    "Evolution completed without "
                            + "a valid target domain.";
        }

        DomainState state =
                domains.get(targetDomain);

        int before =
                result.beforeScore();

        int after =
                result.afterScore();

        int improvement =
                after - before;

        state.lastBenchmark =
                after;

        long gain =
                calculatePowerGain(
                        state,
                        improvement,
                        result.improved()
                );

        if (gain > 0) {

            long oldPower =
                    state.power;

            state.power += gain;

            totalPower += gain;

            state.cycles++;

            evolutionCycles++;

            saveState();

            writeLog(
                    "+"
                            + gain
                            + " "
                            + targetDomain.name()
                            + " Power | Benchmark "
                            + before
                            + " → "
                            + after
            );

            return
                    "Evolution successful."
                            + System.lineSeparator()
                            + "Domain: "
                            + targetDomain.name()
                            + System.lineSeparator()
                            + "Benchmark: "
                            + before
                            + " → "
                            + after
                            + System.lineSeparator()
                            + "Power: "
                            + oldPower
                            + " → "
                            + state.power
                            + System.lineSeparator()
                            + "Gain: +"
                            + gain;
        }

        state.cycles++;

        evolutionCycles++;

        saveState();

        writeLog(
                "No measurable improvement | "
                        + targetDomain.name()
                        + " | Benchmark "
                        + before
                        + " → "
                        + after
        );

        return
                "Evolution cycle completed."
                        + System.lineSeparator()
                        + "Domain: "
                        + targetDomain.name()
                        + System.lineSeparator()
                        + "Benchmark: "
                        + after
                        + "/100"
                        + System.lineSeparator()
                        + "Power unchanged.";
    }

    private long calculatePowerGain(
            DomainState state,
            int improvement,
            boolean improved
    ) {

        if (!improved
                || improvement <= 0) {

            return 0;
        }

        /*
         * Power has no artificial upper limit.
         *
         * The benchmark is bounded to 100,
         * but Power itself is open-ended.
         */

        long gain =
                Math.max(
                        1,
                        improvement
                );

        /*
         * Higher-priority domains receive
         * a slightly larger reward.
         */

        if (state.priority >= 90) {

            gain += 2;

        } else if (state.priority >= 70) {

            gain += 1;
        }

        return gain;
    }

    private DomainState selectNextDomain() {

        return domains.values()
                .stream()
                .filter(
                        state ->
                                state.enabled
                )
                .max(
                        Comparator
                                .comparingInt(
                                        (DomainState state) ->
                                                state.priority
                                )
                                .thenComparingLong(
                                        state ->
                                                -state.cycles
                                )
                )
                .orElse(null);
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
                            .toUpperCase()
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    private void initializeDomains() {

        for (Domain domain :
                Domain.values()) {

            domains.put(
                    domain,
                    new DomainState(
                            domain,
                            true,
                            50,
                            0,
                            0,
                            0
                    )
            );
        }

        domains.get(
                Domain.CODING
        ).priority = 100;

        domains.get(
                Domain.REASONING
        ).priority = 80;

        domains.get(
                Domain.RESEARCH
        ).priority = 70;

        domains.get(
                Domain.KNOWLEDGE
        ).priority = 60;

        domains.get(
                Domain.MEMORY
        ).priority = 50;
    }

    private void saveState() {

        try {

            List<String> lines =
                    new ArrayList<>();

            lines.add(
                    "running="
                            + running
            );

            lines.add(
                    "totalPower="
                            + totalPower
            );

            lines.add(
                    "evolutionCycles="
                            + evolutionCycles
            );

            for (Domain domain :
                    Domain.values()) {

                DomainState state =
                        domains.get(domain);

                lines.add(
                        "domain."
                                + domain.name()
                                + ".enabled="
                                + state.enabled
                );

                lines.add(
                        "domain."
                                + domain.name()
                                + ".priority="
                                + state.priority
                );

                lines.add(
                        "domain."
                                + domain.name()
                                + ".power="
                                + state.power
                );

                lines.add(
                        "domain."
                                + domain.name()
                                + ".cycles="
                                + state.cycles
                );

                lines.add(
                        "domain."
                                + domain.name()
                                + ".benchmark="
                                + state.lastBenchmark
                );
            }

            Files.write(
                    EVOLUTION_FILE,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException ignored) {
        }
    }

    private void loadState() {

        if (!Files.exists(
                EVOLUTION_FILE
        )) {

            saveState();

            return;
        }

        try {

            List<String> lines =
                    Files.readAllLines(
                            EVOLUTION_FILE,
                            StandardCharsets.UTF_8
                    );

            for (String line :
                    lines) {

                if (line.startsWith(
                        "running="
                )) {

                    running =
                            Boolean.parseBoolean(
                                    valueOf(line)
                            );

                } else if (line.startsWith(
                        "totalPower="
                )) {

                    totalPower =
                            parseLong(
                                    valueOf(line),
                                    0
                            );

                } else if (line.startsWith(
                        "evolutionCycles="
                )) {

                    evolutionCycles =
                            parseLong(
                                    valueOf(line),
                                    0
                            );

                } else {

                    loadDomainValue(line);
                }
            }

        } catch (Exception ignored) {
        }
    }

    private void loadDomainValue(
            String line
    ) {

        for (Domain domain :
                Domain.values()) {

            String prefix =
                    "domain."
                            + domain.name();

            if (!line.startsWith(
                    prefix
            )) {

                continue;
            }

            DomainState state =
                    domains.get(domain);

            if (line.endsWith(
                    ".enabled"
            )) {

                state.enabled =
                        Boolean.parseBoolean(
                                valueOf(line)
                        );

            } else if (line.endsWith(
                    ".priority"
            )) {

                state.priority =
                        (int) parseLong(
                                valueOf(line),
                                50
                        );

            } else if (line.endsWith(
                    ".power"
            )) {

                state.power =
                        parseLong(
                                valueOf(line),
                                0
                        );

            } else if (line.endsWith(
                    ".cycles"
            )) {

                state.cycles =
                        parseLong(
                                valueOf(line),
                                0
                        );

            } else if (line.endsWith(
                    ".benchmark"
            )) {

                state.lastBenchmark =
                        (int) parseLong(
                                valueOf(line),
                                0
                        );
            }
        }
    }

    private String valueOf(
            String line
    ) {

        int index =
                line.indexOf('=');

        if (index < 0) {
            return "";
        }

        return line.substring(
                index + 1
        ).trim();
    }

    private long parseLong(
            String value,
            long fallback
    ) {

        try {

            return Long.parseLong(
                    value
            );

        } catch (Exception e) {

            return fallback;
        }
    }

    private void writeLog(
            String message
    ) {

        try {

            String entry =
                    "["
                            + Instant.now()
                            + "] "
                            + message
                            + System.lineSeparator();

            Files.writeString(
                    LOG_FILE,
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException ignored) {
        }
    }

    public enum Domain {

        KNOWLEDGE,
        REASONING,
        RESEARCH,
        CODING,
        MEMORY
    }

    private static class DomainState {

        private final Domain domain;

        private boolean enabled;

        private int priority;

        private long power;

        private long cycles;

        private int lastBenchmark;

        private DomainState(
                Domain domain,
                boolean enabled,
                int priority,
                long power,
                long cycles,
                int lastBenchmark
        ) {
            this.domain = domain;
            this.enabled = enabled;
            this.priority = priority;
            this.power = power;
            this.cycles = cycles;
            this.lastBenchmark = lastBenchmark;
        }
    }

    public record DomainSnapshot(
            boolean enabled,
            int priority,
            long power,
            long cycles,
            int lastBenchmark
    ) {
    }

    public record EvolutionSnapshot(
            long totalPower,
            boolean running,
            long evolutionCycles,
            Map<String, DomainSnapshot> domains
    ) {
    }
}
