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
import java.util.List;

@Service
public class ImprovementSandbox {

    private static final Path SANDBOX_DIR =
            Path.of("blackwater-sandbox");

    private static final Path EXPERIMENTS_DIR =
            SANDBOX_DIR.resolve("experiments");

    private static final Path RESULTS_DIR =
            SANDBOX_DIR.resolve("results");

    private static final int MAX_EXPERIMENTS = 50;

    public synchronized SandboxResult createExperiment(
            String name,
            String sourceCode
    ) {

        if (name == null
                || name.isBlank()) {

            return failed(
                    "Experiment name is empty."
            );
        }

        if (sourceCode == null
                || sourceCode.isBlank()) {

            return failed(
                    "Source code is empty."
            );
        }

        String safeName =
                sanitizeName(name);

        try {

            Files.createDirectories(
                    EXPERIMENTS_DIR
            );

            Files.createDirectories(
                    RESULTS_DIR
            );

            cleanupOldExperiments();

            String id =
                    Instant.now()
                            .toEpochMilli()
                            + "-"
                            + safeName;

            Path experimentDir =
                    EXPERIMENTS_DIR.resolve(id);

            Files.createDirectories(
                    experimentDir
            );

            Path sourceFile =
                    experimentDir.resolve(
                            "Source.java"
                    );

            Files.writeString(
                    sourceFile,
                    sourceCode,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            return new SandboxResult(
                    id,
                    true,
                    "Experiment created.",
                    experimentDir.toString(),
                    List.of()
            );

        } catch (IOException e) {

            return failed(
                    "Could not create experiment: "
                            + e.getMessage()
            );
        }
    }

    public synchronized SandboxResult inspect(
            String experimentId
    ) {

        Path experiment =
                getExperiment(
                        experimentId
                );

        if (experiment == null) {

            return failed(
                    "Experiment not found."
            );
        }

        try {

            List<String> files =
                    Files.walk(experiment)
                            .filter(
                                    Files::isRegularFile
                            )
                            .map(
                                    Path::toString
                            )
                            .toList();

            return new SandboxResult(
                    experimentId,
                    true,
                    "Experiment inspected.",
                    experiment.toString(),
                    files
            );

        } catch (IOException e) {

            return failed(
                    "Could not inspect experiment: "
                            + e.getMessage()
            );
        }
    }

    public synchronized SandboxResult writeFile(
            String experimentId,
            String relativePath,
            String content
    ) {

        if (relativePath == null
                || relativePath.isBlank()) {

            return failed(
                    "File path is empty."
            );
        }

        if (content == null) {

            return failed(
                    "File content is null."
            );
        }

        Path experiment =
                getExperiment(
                        experimentId
                );

        if (experiment == null) {

            return failed(
                    "Experiment not found."
            );
        }

        Path file =
                experiment.resolve(
                        relativePath
                ).normalize();

        if (!file.startsWith(
                experiment.normalize()
        )) {

            return failed(
                    "Invalid sandbox path."
            );
        }

        try {

            Files.createDirectories(
                    file.getParent()
            );

            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            return new SandboxResult(
                    experimentId,
                    true,
                    "File written.",
                    file.toString(),
                    List.of(
                            relativePath
                    )
            );

        } catch (IOException e) {

            return failed(
                    "Could not write file: "
                            + e.getMessage()
            );
        }
    }

    public synchronized String readFile(
            String experimentId,
            String relativePath
    ) {

        if (relativePath == null
                || relativePath.isBlank()) {

            return "";
        }

        Path experiment =
                getExperiment(
                        experimentId
                );

        if (experiment == null) {

            return "";
        }

        Path file =
                experiment.resolve(
                        relativePath
                ).normalize();

        if (!file.startsWith(
                experiment.normalize()
        )) {

            return "";
        }

        try {

            if (!Files.exists(file)
                    || !Files.isRegularFile(file)) {

                return "";
            }

            return Files.readString(
                    file,
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {

            return "";
        }
    }

    public synchronized SandboxResult recordResult(
            String experimentId,
            boolean passed,
            String details
    ) {

        Path experiment =
                getExperiment(
                        experimentId
                );

        if (experiment == null) {

            return failed(
                    "Experiment not found."
            );
        }

        try {

            Files.createDirectories(
                    RESULTS_DIR
            );

            String result =
                    "timestamp="
                            + Instant.now()
                            + System.lineSeparator()
                            + "experiment="
                            + experimentId
                            + System.lineSeparator()
                            + "passed="
                            + passed
                            + System.lineSeparator()
                            + "details="
                            + (
                                    details == null
                                            ? ""
                                            : details
                            )
                            + System.lineSeparator();

            Path resultFile =
                    RESULTS_DIR.resolve(
                            experimentId + ".txt"
                    );

            Files.writeString(
                    resultFile,
                    result,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            return new SandboxResult(
                    experimentId,
                    true,
                    passed
                            ? "Experiment passed."
                            : "Experiment failed.",
                    resultFile.toString(),
                    List.of()
            );

        } catch (IOException e) {

            return failed(
                    "Could not record result: "
                            + e.getMessage()
            );
        }
    }

    public synchronized List<String> listExperiments() {

        if (!Files.exists(
                EXPERIMENTS_DIR
        )) {

            return List.of();
        }

        try {

            return Files.list(
                            EXPERIMENTS_DIR
                    )
                    .filter(
                            Files::isDirectory
                    )
                    .map(
                            path ->
                                    path.getFileName()
                                            .toString()
                    )
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .toList();

        } catch (IOException e) {

            return List.of();
        }
    }

    public synchronized boolean exists(
            String experimentId
    ) {

        return getExperiment(
                experimentId
        ) != null;
    }

    public synchronized void deleteExperiment(
            String experimentId
    ) {

        Path experiment =
                getExperiment(
                        experimentId
                );

        if (experiment == null) {
            return;
        }

        try {

            Files.walk(experiment)
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(
                                            path
                                    );
                                } catch (IOException ignored) {
                                }
                            }
                    );

        } catch (IOException ignored) {
        }
    }

    private Path getExperiment(
            String experimentId
    ) {

        if (experimentId == null
                || experimentId.isBlank()) {

            return null;
        }

        if (!experimentId.matches(
                "[a-zA-Z0-9_-]+"
        )) {

            return null;
        }

        Path experiment =
                EXPERIMENTS_DIR.resolve(
                        experimentId
                ).normalize();

        if (!experiment.startsWith(
                EXPERIMENTS_DIR.normalize()
        )) {

            return null;
        }

        if (!Files.exists(experiment)
                || !Files.isDirectory(experiment)) {

            return null;
        }

        return experiment;
    }

    private String sanitizeName(
            String name
    ) {

        String safe =
                name.trim()
                        .replaceAll(
                                "[^a-zA-Z0-9_-]+",
                                "-"
                        );

        if (safe.isBlank()) {
            safe = "experiment";
        }

        return safe.substring(
                0,
                Math.min(
                        safe.length(),
                        80
                )
        );
    }

    private void cleanupOldExperiments()
            throws IOException {

        if (!Files.exists(
                EXPERIMENTS_DIR
        )) {

            return;
        }

        List<Path> experiments =
                Files.list(
                        EXPERIMENTS_DIR
                )
                .filter(
                        Files::isDirectory
                )
                .sorted(
                        Comparator.comparing(
                                path -> {
                                    try {
                                        return Files.getLastModifiedTime(
                                                path
                                        );
                                    } catch (IOException e) {
                                        return null;
                                    }
                                }
                        )
                )
                .toList();

        int excess =
                experiments.size()
                        - MAX_EXPERIMENTS
                        + 1;

        for (int i = 0;
             i < excess;
             i++) {

            deletePath(
                    experiments.get(i)
            );
        }
    }

    private void deletePath(
            Path path
    ) {

        try {

            Files.walk(path)
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(
                            item -> {
                                try {
                                    Files.deleteIfExists(
                                            item
                                    );
                                } catch (IOException ignored) {
                                }
                            }
                    );

        } catch (IOException ignored) {
        }
    }

    private SandboxResult failed(
            String message
    ) {

        return new SandboxResult(
                "",
                false,
                message,
                "",
                new ArrayList<>()
        );
    }

    public record SandboxResult(
            String experimentId,
            boolean success,
            String message,
            String location,
            List<String> files
    ) {
    }
}
