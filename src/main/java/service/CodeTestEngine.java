package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CodeTestEngine {

    private final ImprovementSandbox sandbox;
    private final ImprovementVerifier verifier;

    public CodeTestEngine(
            ImprovementSandbox sandbox,
            ImprovementVerifier verifier
    ) {
        this.sandbox = sandbox;
        this.verifier = verifier;
    }

    public synchronized TestResult test(
            String experimentId
    ) {

        if (experimentId == null
                || experimentId.isBlank()) {

            return failed(
                    "",
                    "Experiment ID is empty."
            );
        }

        if (!sandbox.exists(experimentId)) {

            return failed(
                    experimentId,
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
                    experimentId,
                    "Sandbox inspection failed."
            );
        }

        if (!inspection.success()) {

            return failed(
                    experimentId,
                    inspection.message()
            );
        }

        List<String> passed =
                new ArrayList<>();

        List<String> failed =
                new ArrayList<>();

        String text =
                inspection.message() == null
                        ? ""
                        : inspection.message();

        testForUnsafeContent(
                text,
                passed,
                failed
        );

        testForGeneratedFiles(
                text,
                passed,
                failed
        );

        testForBasicSourceValidity(
                text,
                passed,
                failed
        );

        ImprovementVerifier.VerificationResult verification =
                verifier.verify(
                        experimentId
                );

        if (verification.passed()) {

            passed.add(
                    "Sandbox verification passed."
            );

        } else {

            failed.add(
                    "Sandbox verification failed."
            );
        }

        boolean success =
                failed.isEmpty();

        String resultMessage =
                success
                        ? "All safe tests passed."
                        : "One or more tests failed.";

        try {

            sandbox.recordResult(
                    experimentId,
                    success,
                    resultMessage
            );

        } catch (Exception ignored) {
        }

        return new TestResult(
                experimentId,
                success,
                passed,
                failed
        );
    }

    private void testForUnsafeContent(
            String text,
            List<String> passed,
            List<String> failed
    ) {

        String lower =
                text.toLowerCase(
                        Locale.ROOT
                );

        boolean unsafe =
                lower.contains(
                        "runtime.getruntime"
                )
                || lower.contains(
                        "processbuilder"
                )
                || lower.contains(
                        "rm -rf"
                )
                || lower.contains(
                        "format c:"
                )
                || lower.contains(
                        "shutdown"
                )
                || lower.contains(
                        "powershell"
                )
                || lower.contains(
                        "cmd.exe"
                );

        if (unsafe) {

            failed.add(
                    "Unsafe operation detected."
            );

        } else {

            passed.add(
                    "No forbidden operations detected."
            );
        }
    }

    private void testForGeneratedFiles(
            String text,
            List<String> passed,
            List<String> failed
    ) {

        if (text.isBlank()) {

            failed.add(
                    "Sandbox contains no inspectable content."
            );

            return;
        }

        passed.add(
                "Sandbox contains generated content."
        );
    }

    private void testForBasicSourceValidity(
            String text,
            List<String> passed,
            List<String> failed
    ) {

        int braces =
                count(
                        text,
                        '{'
                );

        int closingBraces =
                count(
                        text,
                        '}'
                );

        if (braces != closingBraces) {

            failed.add(
                    "Unbalanced braces detected."
            );

        } else {

            passed.add(
                    "Basic brace structure is valid."
            );
        }

        int parentheses =
                count(
                        text,
                        '('
                );

        int closingParentheses =
                count(
                        text,
                        ')'
                );

        if (parentheses != closingParentheses) {

            failed.add(
                    "Unbalanced parentheses detected."
            );

        } else {

            passed.add(
                    "Basic parentheses structure is valid."
            );
        }
    }

    private int count(
            String text,
            char target
    ) {

        int result = 0;

        for (int i = 0;
             i < text.length();
             i++) {

            if (text.charAt(i) == target) {
                result++;
            }
        }

        return result;
    }

    private TestResult failed(
            String experimentId,
            String message
    ) {

        return new TestResult(
                experimentId,
                false,
                List.of(),
                List.of(message)
        );
    }

    public record TestResult(
            String experimentId,
            boolean passed,
            List<String> passedTests,
            List<String> failedTests
    ) {
    }
}
