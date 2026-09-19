package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeChangeEngine {

    private final ImprovementSandbox sandbox;

    public CodeChangeEngine(
            ImprovementSandbox sandbox
    ) {
        this.sandbox = sandbox;
    }

    public synchronized ChangeResult createChangeSet(
            ImprovementGenerator.ImprovementProposal proposal
    ) {

        if (proposal == null) {

            return failed(
                    "No improvement proposal supplied."
            );
        }

        if (!proposal.generated()) {

            return failed(
                    "Improvement proposal was not generated."
            );
        }

        String feature =
                proposal.featureName();

        if (feature == null
                || feature.isBlank()) {

            return failed(
                    "Improvement feature is empty."
            );
        }

        String experimentName =
                "improvement-"
                        + sanitize(feature);

        String source =
                buildManifest(
                        proposal
                );

        ImprovementSandbox.SandboxResult experiment =
                sandbox.createExperiment(
                        experimentName,
                        source
                );

        if (!experiment.success()) {

            return failed(
                    experiment.message()
            );
        }

        List<ChangeRequest> changes =
                createChangeRequests(
                        proposal
                );

        List<String> writtenFiles =
                new ArrayList<>();

        for (ChangeRequest change :
                changes) {

            ImprovementSandbox.SandboxResult result =
                    sandbox.writeFile(
                            experiment.experimentId(),
                            change.relativePath(),
                            change.content()
                    );

            if (!result.success()) {

                sandbox.recordResult(
                        experiment.experimentId(),
                        false,
                        "Could not write: "
                                + change.relativePath()
                );

                return new ChangeResult(
                        experiment.experimentId(),
                        false,
                        "Change set creation failed.",
                        writtenFiles,
                        changes
                );
            }

            writtenFiles.add(
                    change.relativePath()
            );
        }

        sandbox.recordResult(
                experiment.experimentId(),
                true,
                "Change set created with "
                        + writtenFiles.size()
                        + " files."
        );

        return new ChangeResult(
                experiment.experimentId(),
                true,
                "Change set created in sandbox.",
                writtenFiles,
                changes
        );
    }

    private List<ChangeRequest> createChangeRequests(
            ImprovementGenerator.ImprovementProposal proposal
    ) {

        List<ChangeRequest> result =
                new ArrayList<>();

        String summary =
                clean(
                        proposal.summary()
                );

        String feature =
                clean(
                        proposal.featureName()
                );

        String domain =
                clean(
                        proposal.domain()
                );

        StringBuilder plan =
                new StringBuilder();

        plan.append(
                "BLACKWATER IMPROVEMENT PLAN"
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Domain: "
        )
        .append(
                domain
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Feature: "
        )
        .append(
                feature
        )
        .append(
                System.lineSeparator()
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Summary:"
        )
        .append(
                System.lineSeparator()
        )
        .append(
                summary
        )
        .append(
                System.lineSeparator()
        )
        .append(
                System.lineSeparator()
        )
        .append(
                "Relevant files:"
        )
        .append(
                System.lineSeparator()
        );

        for (String file :
                proposal.relevantFiles()) {

            plan.append(
                    "- "
            )
            .append(
                    file
            )
            .append(
                    System.lineSeparator()
            );
        }

        plan.append(
                System.lineSeparator()
        )
        .append(
                "Proposed files:"
        )
        .append(
                System.lineSeparator()
        );

        for (String file :
                proposal.proposedFiles()) {

            plan.append(
                    "- "
            )
            .append(
                    file
            )
            .append(
                    System.lineSeparator()
            );
        }

        plan.append(
                System.lineSeparator()
        )
        .append(
                "Required tests:"
        )
        .append(
                System.lineSeparator()
        );

        for (String test :
                proposal.tests()) {

            plan.append(
                    "- "
            )
            .append(
                    test
            )
            .append(
                    System.lineSeparator()
            );
        }

        result.add(
                new ChangeRequest(
                        "improvement-plan.txt",
                        plan.toString()
                )
        );

        result.add(
                new ChangeRequest(
                        "change-status.txt",
                        """
                        STATUS=PROPOSED
                        SAFE_TO_APPLY=false
                        TESTED=false
                        APPROVED=false
                        FEATURE=%s
                        DOMAIN=%s
                        """.formatted(
                                feature,
                                domain
                        )
                )
        );

        return result;
    }

    private String buildManifest(
            ImprovementGenerator.ImprovementProposal proposal
    ) {

        return """
                Blackwater isolated improvement experiment.

                Feature:
                %s

                Domain:
                %s

                This directory is a sandbox experiment.
                No files in the main project are modified here.

                Generated proposal:
                %s
                """.formatted(
                proposal.featureName(),
                proposal.domain(),
                proposal.summary()
        ).trim();
    }

    private String sanitize(
            String value
    ) {

        String result =
                value
                        .trim()
                        .replaceAll(
                                "[^a-zA-Z0-9_-]+",
                                "-"
                        );

        if (result.isBlank()) {
            return "feature";
        }

        return result.substring(
                0,
                Math.min(
                        result.length(),
                        60
                )
        );
    }

    private String clean(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace(
                        "\u0000",
                        ""
                )
                .trim();
    }

    private ChangeResult failed(
            String message
    ) {

        return new ChangeResult(
                "",
                false,
                message,
                List.of(),
                List.of()
        );
    }

    public record ChangeRequest(
            String relativePath,
            String content
    ) {
    }

    public record ChangeResult(
            String experimentId,
            boolean success,
            String message,
            List<String> writtenFiles,
            List<ChangeRequest> changes
    ) {
    }
}
