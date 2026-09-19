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
import java.util.Map;

@Service
public class EvolutionEngine {

    private static final Path EVOLUTION_FILE =
            Path.of("blackwater-evolution.txt");

    private static final Path LOG_FILE =
            Path.of("blackwater-evolution-log.txt");

    private static final long DEFAULT_POWER = 0;

    private final LearningEngine learningEngine;

    private final Map<Domain, DomainState> domains =
            new EnumMap<>(Domain.class);

    private boolean running = false;

    private long totalPower =
            DEFAULT_POWER;

    private long evolutionCycles = 0;

    public EvolutionEngine(
            LearningEngine learningEngine
    ) {
        this.learningEngine =
                learningEngine;

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

        DomainState state =
                domains.get(parsed);

        state.enabled =
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

        DomainState state =
                domains.get(parsed);

        state.priority =
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
                new java.util.LinkedHashMap<>();

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
                            state.cycles
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

            return
                    "Evolution is paused.";
        }

        DomainState target =
                selectNextDomain();

        if (target == null) {

            return
                    "No evolution domains are enabled.";
        }

        String result =
                evolveDomain(
                        target
                );

        evolutionCycles++;

        saveState();

        return result;
    }

    private String evolveDomain(
            DomainState state
    ) {

        /*
         * This is the safe foundation of the
         * self-improvement system.
         *
         * Later benchmark engines will replace
         * the temporary learning action with
         * measurable tests.
         */

        String topic =
                switch (state.domain) {

                    case KNOWLEDGE ->
                            "knowledge retrieval and information quality";

                    case REASONING ->
                            "reasoning and logical problem solving";

                    case RESEARCH ->
                            "web research and source verification";

                    case CODING ->
                            "software development and programming";

                    case MEMORY ->
                            "memory retrieval and context management";
                };

        long oldPower =
                state.power;

        String result;

        try {

            result =
                    learningEngine.learn(
                            topic
                    );

        } catch (Exception e) {

            result =
                    "Evolution attempt failed: "
                            + e.getMessage();
        }

        /*
         * Temporary conservative growth.
         *
         * Real benchmark-based Power changes
         * will be added in the next stages.
         */

        boolean useful =
                result != null
                        && !result.isBlank()
                        && !result.contains(
                                "No information"
                        )
                        && !result.contains(
                                "not useful"
                        );

        if (useful) {

            long gain =
                    calculateTemporaryGain(
                            state
                    );

            state.power += gain;

            totalPower += gain;

            state.cycles++;

            writeLog(
                    "+"
                            + gain
                            + " "
                            + state.domain.name()
                            + " Power | "
                            + "Previous: "
                            + oldPower
                            + " | New: "
                            + state.power
                            + " | "
                            + result
            );

            return
                    "Evolution successful."
                            + System.lineSeparator()
                            + "Domain: "
                            + state.domain.name()
                            + System.lineSeparator()
                            + "Power: "
                            + oldPower
                            + " → "
                            + state.power
                            + System.lineSeparator()
                            + "Gain: +"
                            + gain;
        }

        writeLog(
                "No measurable improvement in "
                        + state.domain.name()
        );

        return
                "No measurable improvement.";
    }

    private long calculateTemporaryGain(
            DomainState state
    ) {

        /*
         * Priority affects how strongly this
         * domain is selected and how much it
         * can gain during the temporary phase.
         *
         * This will later be replaced by
         * benchmark-based scoring.
         */

        if (state.priority >= 90) {
            return 5;
        }

        if (state.priority >= 70) {
            return 4;
        }

        if (state.priority >= 40) {
            return 3;
        }

        return 2;
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
                            0
                    )
            );
        }

        /*
         * Default priority:
         * Coding receives the highest priority.
         */

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

            for (String line : lines) {

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
                                    DEFAULT_POWER
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

                    loadDomainValue(
                            line
                    );
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

        private DomainState(
                Domain domain,
                boolean enabled,
                int priority,
                long power,
                long cycles
        ) {
            this.domain = domain;
            this.enabled = enabled;
            this.priority = priority;
            this.power = power;
            this.cycles = cycles;
        }
    }

    public record DomainSnapshot(
            boolean enabled,
            int priority,
            long power,
            long cycles
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
