package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImprovementVerifier {

    private final ImprovementSandbox sandbox;

    public ImprovementVerifier(
            ImprovementSandbox sandbox
    ) {
        this.sandbox = sandbox;
    }

    public synchronized VerificationResult verify(
            String experimentId
    ) {

        if (experimentId == null
                || experimentId.isBlank()) {

            return failed(
                    "Experiment ID is empty."
            );
        }

        if (!sandbox.exists(experimentId)) {

            return failed(
                    "Experiment does not exist."
            );
        }

        ImprovementSandbox.SandboxResult inspection;

        try {

            inspection =
                    sandbox.inspect(
                            experimentId
                    );

        } catch (Exception e) {

            return failed(
                    "Sandbox inspection failed."
            );
        }

        if (!inspection.success()) {

            return failed(
                    inspection.message()
            );
        }

        List<String> errors =
                new ArrayList<>();

        List<String> warnings =
                new ArrayList<>();

        String inspectionText =
                inspection.message() == null
                        ? ""
                        : inspection.message();

        if (inspectionText.isBlank()) {

            warnings.add(
                    "Sandbox inspection returned no details."
            );
        }

        if (containsUnsafePath(
                inspectionText
        )) {

            errors.add(
                    "Unsafe path detected."
            );
        }

        if (containsForbiddenOperation(
                inspectionText
        )) {

            errors.add(
                    "Potentially unsafe operation detected."
            );
        }

        boolean passed =
                errors.isEmpty();

        String status =
                passed
                        ? "VERIFICATION_PASSED"
                        : "VERIFICATION_FAILED";

        try {

            sandbox.recordResult(
                    experimentId,
                    passed,
                    buildResultMessage(
                            status,
                            errors,
                            warnings
                    )
            );

        } catch (Exception ignored) {
        }

        return new VerificationResult(
                experimentId,
                passed,
                status,
                errors,
                warnings
        );
    }

    private String buildResultMessage(
            String status,
            List<String> errors,
            List<String> warnings
    ) {

        StringBuilder result =
                new StringBuilder();

        result.append(status)
                .append(System.lineSeparator());

        result.append(
                "Errors: "
        )
        .append(
                errors.size()
        )
        .append(
                System.lineSeparator()
        );

        for (String error : errors) {

            result.append(
                    "- "
            )
            .append(error)
            .append(
                    System.lineSeparator()
            );
        }

        result.append(
                "Warnings: "
        )
        .append(
                warnings.size()
        )
        .append(
                System.lineSeparator()
        );

        for (String warning : warnings) {

            result.append(
                    "- "
            )
            .append(warning)
            .append(
                    System.lineSeparator()
            );
        }

        return result.toString().trim();
    }

    private boolean containsUnsafePath(
            String text
    ) {

        String lower =
                text.toLowerCase();

        return lower.contains("../")
                || lower.contains("..\\")
                || lower.contains("/etc/")
                || lower.contains("\\windows\\")
                || lower.contains("system32")
                || lower.contains(
                        "outside project"
                )
                || lower.contains(
                        "outside the project"
                );
    }

    private boolean containsForbiddenOperation(
            String text
    ) {

        String lower =
                text.toLowerCase();

        return lower.contains(
                    "runtime.getruntime"
                )
                || lower.contains(
                        "processbuilder"
                )
                || lower.contains(
                        "powershell"
                )
                || lower.contains(
                        "cmd.exe"
                )
                || lower.contains(
                        "rm -rf"
                )
                || lower.contains(
                        "shutdown"
                )
                || lower.contains(
                        "format c:"
                );
    }

    private VerificationResult failed(
            String message
    ) {

        return new VerificationResult(
                "",
                false,
                "VERIFICATION_FAILED",
                List.of(message),
                List.of()
        );
    }

    public record VerificationResult(
            String experimentId,
            boolean passed,
            String status,
            List<String> errors,
            List<String> warnings
    ) {
    }
}
