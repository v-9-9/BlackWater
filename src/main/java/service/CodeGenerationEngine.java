package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public synchronized GenerationResult generate(
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
                clean(proposal.featureName());

        if (feature.isBlank()) {
            return failed(
                    "Feature name is empty."
            );
        }

        String experimentName =
                "codegen-" + sanitize(feature);

        ImprovementSandbox.SandboxResult experiment =
                sandbox.createExperiment(
                        experimentName,
                        buildManifest(proposal)
                );

        if (!experiment.success()) {
            return failed(
                    experiment.message()
            );
        }

        String experimentId =
                experiment.experimentId();

        List<String> generatedFiles =
                new ArrayList<>();

        List<String> errors =
                new ArrayList<>();

        for (String path :
                proposal.proposedFiles()) {

            String safePath =
                    normalizePath(path);

            if (!isAllowedProjectPath(safePath)) {

                errors.add(
                        "Rejected unsafe path: "
                                + path
                );

                continue;
            }

            String currentSource =
                    readCurrentSource(
                            safePath
                    );

            String prompt =
                    buildCodePrompt(
                            proposal,
                            safePath,
                            currentSource
                    );

            String response;

            try {

                response =
                        aiService.generate(
                                prompt,
                                "deep",
                                null
                        );

            } catch (Exception e) {

                errors.add(
                        "Generation failed for "
                                + safePath
                );

                continue;
            }

            String code =
                    extractCode(response);

            if (code.isBlank()) {

                errors.add(
                        "No valid code generated for "
                                + safePath
                );

                continue;
            }

            ImprovementSandbox.SandboxResult written =
                    sandbox.writeFile(
                            experimentId,
                            safePath,
                            code
                    );

            if (!written.success()) {

                errors.add(
                        "Could not write "
                                + safePath
                );

                continue;
            }

            generatedFiles.add(
                    safePath
            );
        }

        boolean success =
                !generatedFiles.isEmpty()
                        && errors.isEmpty();

        sandbox.recordResult(
                experimentId,
                success,
                success
                        ? "Code generation completed."
                        : "Code generation completed with errors."
        );

        return new GenerationResult(
                experimentId,
                success,
                generatedFiles,
                errors
        );
    }

    private String buildCodePrompt(
            ImprovementGenerator.ImprovementProposal proposal,
            String path,
            String currentSource
    ) {

        return """
                You are Blackwater's code generation engine.

                Generate the complete replacement content for
                exactly one project file.

                Feature:
                %s

                Domain:
                %s

                Improvement summary:
                %s

                Target file:
                %s

                Current file content:
                %s

                Rules:
                - Return ONLY the complete file content.
                - Do not use Markdown fences.
                - Do not explain the code.
                - Preserve existing functionality.
                - Make the smallest safe change possible.
                - Do not add unrelated features.
                - Do not include secrets or API keys.
                - Do not use Runtime.exec.
                - Do not use ProcessBuilder.
                - Do not execute shell commands.
                - Do not access files outside the project.
                - The result must be valid for the target file type.
                """.formatted(
                proposal.featureName(),
                proposal.domain(),
                proposal.summary(),
                path,
                currentSource
        ).trim();
    }

    private String readCurrentSource(
            String path
    ) {

        try {
            String source =
                    codeAnalysisEngine.readSource(
                            path
                    );

            if (source == null) {
                return "(File does not currently exist.)";
            }

            return source;
        } catch (Exception ignored) {
            return "(File could not be read.)";
        }
    }

    private String extractCode(
            String response
    ) {

        if (response == null
                || response.isBlank()) {
            return "";
        }

        String code =
                response.trim();

        if (code.startsWith("```")
                && code.endsWith("```")) {

            int firstNewLine =
                    code.indexOf('\n');

            if (firstNewLine > 0) {

                code =
                        code.substring(
                                firstNewLine + 1,
                                code.length() - 3
                        );
            }
        }

        return code.trim();
    }

    private boolean isAllowedProjectPath(
            String path
    ) {

        return path.startsWith(
                    "src/main/"
                )
                || path.startsWith(
                        "src/test/"
                );
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

    private String sanitize(
            String value
    ) {

        String result =
                value.replaceAll(
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

        return value == null
                ? ""
                : value
                        .replace(
                                "\u0000",
                                ""
                        )
                        .trim();
    }

    private String buildManifest(
            ImprovementGenerator.ImprovementProposal proposal
    ) {

        return """
                Blackwater Code Generation Experiment

                Feature:
                %s

                Domain:
                %s

                Summary:
                %s

                This experiment is isolated.
                Generated code must be verified before application.
                """.formatted(
                proposal.featureName(),
                proposal.domain(),
                proposal.summary()
        ).trim();
    }

    private GenerationResult failed(
            String message
    ) {

        return new GenerationResult(
                "",
                false,
                List.of(),
                List.of(message)
        );
    }

    public record GenerationResult(
            String experimentId,
            boolean success,
            List<String> generatedFiles,
            List<String> errors
    ) {
    }
}
