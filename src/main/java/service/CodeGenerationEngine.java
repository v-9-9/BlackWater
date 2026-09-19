package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CodeGenerationEngine {

    private final AIService aiService;
    private final CodeAnalysisEngine codeAnalysisEngine;
    private final ImprovementSandbox sandbox;

    public CodeGenerationEngine(
            AIService aiService,
            CodeAnalysisEngine codeAnalysisEngine,
            ImprovementSandbox sandbox
    ) {
        this.aiService = aiService;
        this.codeAnalysisEngine = codeAnalysisEngine;
        this.sandbox = sandbox;
    }

    public GenerationResult generate(
            ImprovementGenerator.ImprovementProposal proposal
    ) {
        if (proposal == null) {
            return new GenerationResult(
                    "",
                    false,
                    List.of(),
                    List.of("Proposal is null.")
            );
        }

        String experimentId;

        try {
            experimentId =
                    sandbox.createExperiment(
                            "code-generation-"
                                    + safe(proposal.featureName())
            );
        } catch (Exception exception) {
            return new GenerationResult(
                    "",
                    false,
                    List.of(),
                    List.of(
                            "Could not create sandbox: "
                                    + safe(exception.getMessage())
                    )
            );
        }

        List<String> generatedFiles =
                new ArrayList<>();

        List<String> errors =
                new ArrayList<>();

        List<String> requestedFiles =
                proposal.proposedFiles();

        if (requestedFiles == null
                || requestedFiles.isEmpty()) {

            requestedFiles =
                    proposal.relevantFiles();
        }

        if (requestedFiles == null
                || requestedFiles.isEmpty()) {

            return new GenerationResult(
                    experimentId,
                    false,
                    generatedFiles,
                    List.of(
                            "No files were selected for generation."
                    )
            );
        }

        for (String relativePath :
                requestedFiles) {

            String path =
                    normalizePath(relativePath);

            if (!isAllowedPath(path)) {
                errors.add(
                        "Blocked path: " + path
                );
                continue;
            }

            String currentSource =
                    codeAnalysisEngine.readSource(
                            path
                    );

            String prompt =
                    buildGenerationPrompt(
                            proposal,
                            path,
                            currentSource
                    );

            String generated;

            try {
                generated =
                        aiService.generate(
                                prompt,
                                "prime",
                                null
                        );
            } catch (Exception exception) {
                errors.add(
                        "Generation failed for "
                                + path
                                + ": "
                                + safe(
                                exception.getMessage()
                        )
                );
                continue;
            }

            generated =
                    cleanGeneratedCode(
                            generated
                    );

            if (generated.isBlank()) {
                errors.add(
                        "Empty generated code for: "
                                + path
                );
                continue;
            }

            if (looksUnsafe(generated)) {
                errors.add(
                        "Unsafe generated code blocked: "
                                + path
                );
                continue;
            }

            try {
                sandbox.writeFile(
                        experimentId,
                        path,
                        generated
                );

                generatedFiles.add(path);

            } catch (Exception exception) {
                errors.add(
                        "Could not write "
                                + path
                                + ": "
                                + safe(
                                exception.getMessage()
                        )
                );
            }
        }

        boolean success =
                !generatedFiles.isEmpty()
                        && errors.isEmpty();

        sandbox.recordResult(
                experimentId,
                success
                        ? "GENERATION_SUCCESS"
                        : "GENERATION_PARTIAL_OR_FAILED"
        );

        return new GenerationResult(
                experimentId,
                success,
                List.copyOf(generatedFiles),
                List.copyOf(errors)
        );
    }

    private String buildGenerationPrompt(
            ImprovementGenerator.ImprovementProposal proposal,
            String path,
            String currentSource
    ) {
        boolean ui =
                isUiFile(path);

        StringBuilder prompt =
                new StringBuilder();

        prompt.append(
                """
                You are Blackwater's controlled code-generation engine.

                Generate the COMPLETE replacement content for exactly ONE file.

                You must preserve existing functionality unless the requested
                improvement explicitly requires changing it.

                Never return Markdown.
                Never wrap the result in ``` fences.
                Never return explanations before or after the code.

                The generated file must be directly usable as the complete
                contents of the requested file.

                """
        );

        prompt.append(
                "TARGET DOMAIN: "
        );
        prompt.append(
                safe(proposal.domain())
        );
        prompt.append("\n");

        prompt.append(
                "FEATURE: "
        );
        prompt.append(
                safe(proposal.featureName())
        );
        prompt.append("\n");

        prompt.append(
                "SUMMARY: "
        );
        prompt.append(
                safe(proposal.summary())
        );
        prompt.append("\n");

        prompt.append(
                "TARGET FILE: "
        );
        prompt.append(path);
        prompt.append("\n\n");

        if (ui) {
            prompt.append(
                    """
                    UI CODING RULES:
                    - Treat this as a real user-facing interface.
                    - Improve visual hierarchy, spacing, responsiveness,
                      usability and consistency when appropriate.
                    - Preserve working controls and API integrations.
                    - Make mobile layouts work properly.
                    - Avoid unnecessary libraries.
                    - Keep the existing Blackwater visual identity unless
                      the requested feature requires a change.
                    - HTML, CSS and JavaScript are all valid UI code.
                    - Do not add voice features.
                    - Do not remove existing functional settings or controls.
                    - Avoid fake UI elements that do nothing.
                    """
            );
        } else {
            prompt.append(
                    """
                    BACKEND CODING RULES:
                    - Preserve existing APIs unless the improvement requires
                      a compatible extension.
                    - Keep classes focused and maintainable.
                    - Validate input where appropriate.
                    - Avoid destructive operations.
                    - Do not introduce shell commands or arbitrary process
                      execution.
                    """
            );
        }

        prompt.append(
                """

                SAFETY RULES:
                - Never use Runtime.getRuntime().
                - Never use ProcessBuilder.
                - Never execute shell commands.
                - Never delete arbitrary files.
                - Never access credentials or secrets.
                - Never modify files outside the project.
                - Never use ../ path traversal.
                - Never add malware, persistence mechanisms or surveillance.
                - Do not modify configuration to expose secrets.

                EXISTING FILE:
                """
        );

        prompt.append(
                "\n"
        );

        if (currentSource == null
                || currentSource.isBlank()) {
            prompt.append(
                    "[FILE DOES NOT CURRENTLY EXIST]"
            );
        } else {
            prompt.append(
                    currentSource
            );
        }

        prompt.append(
                """

                \n\nReturn ONLY the complete replacement
                contents of the target file.
                """
        );

        return prompt.toString();
    }

    private String cleanGeneratedCode(
            String generated
    ) {
        if (generated == null) {
            return "";
        }

        String value =
                generated.trim();

        if (value.startsWith("```")
                && value.endsWith("```")) {

            int firstNewLine =
                    value.indexOf('\n');

            if (firstNewLine > 0) {
                value =
                        value.substring(
                                firstNewLine + 1
                        );
            }

            int lastFence =
                    value.lastIndexOf("```");

            if (lastFence >= 0) {
                value =
                        value.substring(
                                0,
                                lastFence
                        );
            }
        }

        return value.trim();
    }

    private boolean isAllowedPath(
            String path
    ) {
        if (path.isBlank()) {
            return false;
        }

        if (path.contains("..")
                || path.startsWith("/")
                || path.startsWith("\\")
                || path.contains(":")) {
            return false;
        }

        return path.startsWith("src/main/")
                || path.startsWith("src/test/");
    }

    private boolean looksUnsafe(
            String code
    ) {
        String lower =
                safe(code)
                        .toLowerCase(
                                Locale.ROOT
                        );

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
                "wget | sh"
        };

        for (String value : forbidden) {
            if (lower.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private boolean isUiFile(
            String path
    ) {
        String lower =
                safe(path)
                        .toLowerCase(
                                Locale.ROOT
                        );

        return lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".js");
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

    private String safe(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    public record GenerationResult(
            String experimentId,
            boolean success,
            List<String> generatedFiles,
            List<String> errors
    ) {
    }
}
