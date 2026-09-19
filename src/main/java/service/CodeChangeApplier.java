package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class CodeChangeApplier {

    private static final Path PROJECT_ROOT =
            Path.of(".").toAbsolutePath().normalize();

    private static final Path BACKUP_ROOT =
            PROJECT_ROOT.resolve(
                    "blackwater-backups"
            ).normalize();

    private final ImprovementSandbox sandbox;

    public CodeChangeApplier(
            ImprovementSandbox sandbox
    ) {
        this.sandbox = sandbox;
    }

    public ApplyResult apply(
            String experimentId,
            List<String> files
    ) {
        if (experimentId == null
                || experimentId.isBlank()) {
            return failed(
                    "Invalid experiment ID."
            );
        }

        if (files == null
                || files.isEmpty()) {
            return failed(
                    "No files supplied for adoption."
            );
        }

        List<String> applied =
                new ArrayList<>();

        List<String> backups =
                new ArrayList<>();

        List<String> errors =
                new ArrayList<>();

        Path experimentRoot =
                sandboxExperimentPath(
                        experimentId
                );

        if (experimentRoot == null
                || !Files.isDirectory(
                experimentRoot
        )) {
            return failed(
                    "Sandbox experiment was not found."
            );
        }

        String backupId =
                "backup-" + System.currentTimeMillis();

        Path backupDirectory =
                BACKUP_ROOT
                        .resolve(backupId)
                        .normalize();

        try {
            Files.createDirectories(
                    backupDirectory
            );
        } catch (IOException exception) {
            return failed(
                    "Could not create backup directory."
            );
        }

        for (String file :
                files) {

            String relativePath =
                    normalizePath(file);

            if (!isAllowedPath(
                    relativePath
            )) {
                errors.add(
                        "Blocked path: "
                                + relativePath
                );
                continue;
            }

            Path source =
                    experimentRoot
                            .resolve(relativePath)
                            .normalize();

            Path target =
                    PROJECT_ROOT
                            .resolve(relativePath)
                            .normalize();

            if (!source.startsWith(
                    experimentRoot
            )) {
                errors.add(
                        "Sandbox path escaped: "
                                + relativePath
                );
                continue;
            }

            if (!target.startsWith(
                    PROJECT_ROOT
            )) {
                errors.add(
                        "Project path escaped: "
                                + relativePath
                );
                continue;
            }

            try {
                if (!Files.exists(source)
                        || !Files.isRegularFile(
                        source
                )) {
                    errors.add(
                            "Generated file missing: "
                                    + relativePath
                    );
                    continue;
                }

                String generated =
                        Files.readString(
                                source,
                                StandardCharsets.UTF_8
                        );

                if (generated.isBlank()) {
                    errors.add(
                            "Generated file is empty: "
                                    + relativePath
                    );
                    continue;
                }

                if (looksUnsafe(generated)) {
                    errors.add(
                            "Unsafe code blocked: "
                                    + relativePath
                    );
                    continue;
                }

                /*
                 * Backup the current live file
                 * before replacing it.
                 */
                if (Files.exists(target)) {
                    Path backup =
                            backupDirectory
                                    .resolve(
                                            relativePath
                                    )
                                    .normalize();

                    if (!backup.startsWith(
                            backupDirectory
                    )) {
                        errors.add(
                                "Backup path escaped: "
                                        + relativePath
                        );
                        continue;
                    }

                    Path parent =
                            backup.getParent();

                    if (parent != null) {
                        Files.createDirectories(
                                parent
                        );
                    }

                    Files.copy(
                            target,
                            backup,
                            StandardCopyOption
                                    .REPLACE_EXISTING
                    );

                    backups.add(
                            backupRelativePath(
                                    backupDirectory,
                                    backup
                            )
                    );
                }

                Path parent =
                        target.getParent();

                if (parent != null) {
                    Files.createDirectories(
                            parent
                    );
                }

                /*
                 * Atomic-ish replacement:
                 * write a temporary file first,
                 * then replace the live file.
                 */
                Path temporary =
                        target.resolveSibling(
                                target.getFileName()
                                        + ".blackwater.tmp"
                        );

                Files.writeString(
                        temporary,
                        generated,
                        StandardCharsets.UTF_8
                );

                Files.move(
                        temporary,
                        target,
                        StandardCopyOption
                                .REPLACE_EXISTING
                );

                applied.add(
                        relativePath
                );

            } catch (IOException exception) {
                errors.add(
                        "Failed to apply "
                                + relativePath
                                + ": "
                                + safe(
                                exception.getMessage()
                        )
                );
            }
        }

        boolean success =
                !applied.isEmpty()
                        && errors.isEmpty();

        return new ApplyResult(
                success,
                backupId,
                applied,
                backups,
                errors
        );
    }

    public boolean rollback(
            String backupId,
            List<String> files
    ) {
        if (backupId == null
                || backupId.isBlank()
                || files == null
                || files.isEmpty()) {
            return false;
        }

        Path backupDirectory =
                BACKUP_ROOT
                        .resolve(backupId)
                        .normalize();

        if (!backupDirectory.startsWith(
                BACKUP_ROOT
        ) || !Files.isDirectory(
                backupDirectory
        )) {
            return false;
        }

        boolean restoredAny = false;

        for (String file :
                files) {

            String relativePath =
                    normalizePath(file);

            if (!isAllowedPath(
                    relativePath
            )) {
                continue;
            }

            Path backup =
                    backupDirectory
                            .resolve(relativePath)
                            .normalize();

            Path target =
                    PROJECT_ROOT
                            .resolve(relativePath)
                            .normalize();

            if (!backup.startsWith(
                    backupDirectory
            ) || !target.startsWith(
                    PROJECT_ROOT
            )) {
                continue;
            }

            try {
                if (!Files.exists(backup)
                        || !Files.isRegularFile(
                        backup
                )) {
                    continue;
                }

                Path parent =
                        target.getParent();

                if (parent != null) {
                    Files.createDirectories(
                            parent
                    );
                }

                Files.copy(
                        backup,
                        target,
                        StandardCopyOption
                                .REPLACE_EXISTING
                );

                restoredAny = true;

            } catch (IOException ignored) {
            }
        }

        return restoredAny;
    }

    private Path sandboxExperimentPath(
            String experimentId
    ) {
        String safeId =
                experimentId
                        .trim()
                        .replaceAll(
                                "[^a-zA-Z0-9_-]",
                                ""
                        );

        if (safeId.isBlank()) {
            return null;
        }

        /*
         * ImprovementSandbox stores experiments
         * under blackwater-sandbox/experiments.
         */
        Path experiments =
                PROJECT_ROOT
                        .resolve(
                                "blackwater-sandbox"
                        )
                        .resolve(
                                "experiments"
                        )
                        .normalize();

        Path experiment =
                experiments
                        .resolve(safeId)
                        .normalize();

        if (!experiment.startsWith(
                experiments
        )) {
            return null;
        }

        return experiment;
    }

    private boolean isAllowedPath(
            String path
    ) {
        if (path == null
                || path.isBlank()) {
            return false;
        }

        if (path.contains("..")
                || path.startsWith("/")
                || path.startsWith("\\")
                || path.contains(":")) {
            return false;
        }

        return path.startsWith(
                "src/main/"
        ) || path.startsWith(
                "src/test/"
        );
    }

    private boolean looksUnsafe(
            String code
    ) {
        String lower =
                safe(code)
                        .toLowerCase();

        String[] forbidden = {
                "runtime.getruntime",
                "processbuilder",
                "powershell",
                "cmd.exe",
                "rm -rf",
                "format c:",
                "shutdown -",
                "del /f",
                "curl | sh",
                "wget | sh",
                "chmod +x"
        };

        for (String value :
                forbidden) {

            if (lower.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private String normalizePath(
            String path
    ) {
        if (path == null) {
            return "";
        }

        return path
                .trim()
                .replace('\\', '/')
                .replaceAll(
                        "/+",
                        "/"
                );
    }

    private String backupRelativePath(
            Path backupDirectory,
            Path backup
    ) {
        return backupDirectory
                .relativize(backup)
                .toString()
                .replace('\\', '/');
    }

    private ApplyResult failed(
            String message
    ) {
        return new ApplyResult(
                false,
                "",
                List.of(),
                List.of(),
                List.of(message)
        );
    }

    private String safe(
            String value
    ) {
        return value == null
                ? "unknown error"
                : value;
    }

    public record ApplyResult(
            boolean success,
            String backupId,
            List<String> appliedFiles,
            List<String> backupFiles,
            List<String> errors
    ) {
    }
}
