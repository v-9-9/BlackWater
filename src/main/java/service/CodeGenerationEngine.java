package service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    public GenerationResult generate(ImprovementGenerator.ImprovementProposal proposal) {
        if (proposal == null) {
            return new GenerationResult(
                    null,
                    false,
                    List.of(),
                    List.of("Improvement proposal is null.")
            );
        }

        String experimentId = sandbox.createExperiment(
                "code-generation-" + safeName(proposal.featureName()),
                proposal.rawProposal()
        );

        return generate(proposal, experimentId);
    }

    public GenerationResult generate(
            ImprovementGenerator.ImprovementProposal proposal,
            String experimentId
    ) {
        if (proposal == null) {
            return new GenerationResult(
                    experimentId,
                    false,
                    List.of(),
                    List.of("Improvement proposal is null.")
            );
        }

        if (experimentId == null || experimentId.isBlank()) {
            return new GenerationResult(
                    null,
                    false,
                    List.of(),
                    List.of("Experiment ID is missing.")
            );
        }

        if (!sandbox.exists(experimentId)) {
            return new GenerationResult(
                    experimentId,
                    false,
                    List.of(),
                    List.of("Sandbox experiment does not exist.")
            );
        }

        List<String> generatedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Set<String> candidateFiles = new LinkedHashSet<>();

        if (proposal.proposedFiles() != null) {
            candidateFiles.addAll(proposal.proposedFiles());
        }

        if (candidateFiles.isEmpty() && proposal.relevantFiles() != null) {
            candidateFiles.addAll(proposal.relevantFiles());
        }

        if (candidateFiles.isEmpty()) {
            candidateFiles.addAll(
                    codeAnalysisEngine
                            .findRelevantFiles(
                                    proposal.featureName(),
                                    proposal.domain()
                            )
                            .files()
            );
        }

        if (candidateFiles.isEmpty()) {
            errors.add("No relevant source files were found.");
            return new GenerationResult(
                    experimentId,
                    false,
                    generatedFiles,
                    errors
            );
        }

        for (String file : candidateFiles) {
            String normalizedPath = normalizeProjectPath(file);

            if (normalizedPath == null) {
                errors.add("Blocked unsafe or unsupported path: " + file);
                continue;
            }

            try {
                String currentSource = codeAnalysisEngine.readSource(normalizedPath);

                if (currentSource == null || currentSource.isBlank()) {
                    errors.add("Source file is empty or unreadable: " + normalizedPath);
                    continue;
                }

                String prompt = buildGenerationPrompt(
                        proposal,
                        normalizedPath,
                        currentSource
                );

                String generated = aiService.generate(
                        prompt,
                        "prime",
                        null
                );

                String cleaned = cleanGeneratedCode(generated);

                if (!isValidGeneratedContent(cleaned)) {
                    errors.add("Generated content was rejected for: " + normalizedPath);
                    continue;
                }

                if (looksDangerous(cleaned)) {
                    errors.add("Generated content contains blocked operations: " + normalizedPath);
                    continue;
                }

                sandbox.writeFile(
                        experimentId,
                        normalizedPath,
                        cleaned
                );

                generatedFiles.add(normalizedPath);

            } catch (Exception e) {
                errors.add(
                        "Failed to generate " +
                        normalizedPath +
                        ": " +
                        safeMessage(e)
                );
            }
        }

        boolean success = !generatedFiles.isEmpty() && errors.isEmpty();

        if (!success && !generatedFiles.isEmpty()) {
            success = true;
        }

        sandbox.recordResult(
                experimentId,
                "CODE_GENERATION",
                success,
                "Generated files: " + generatedFiles.size()
                        + ", errors: " + errors.size()
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
            String filePath,
            String currentSource
    ) {
        boolean uiFile = isUiFile(filePath);

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                You are Blackwater's controlled code evolution engine.

                Generate a complete replacement for ONE existing project file.

                STRICT RULES:
                1. Return ONLY the complete file content.
                2. Do not use Markdown fences.
                3. Do not explain the code.
                4. Do not output placeholders such as TODO, "...", or omitted sections.
                5. Preserve existing functionality unless the improvement explicitly requires changing it.
                6. Do not add malware, credential theft, surveillance, persistence, destructive commands, or arbitrary command execution.
                7. Do not add Runtime.exec, ProcessBuilder, shell commands, PowerShell, cmd.exe, rm -rf, shutdown, disk formatting, or equivalent destructive behavior.
                8. Do not expose API keys, passwords, tokens, cookies, or private credentials.
                9. Keep the implementation compatible with the existing Blackwater project.
                10. Prefer simple maintainable code over unnecessary dependencies.
                """);

        if (uiFile) {
            prompt.append("""
                    
                    UI-SPECIFIC RULES:
                    11. This is UI/frontend code.
                    12. Improve visual hierarchy, spacing, readability, responsiveness, and interaction quality where appropriate.
                    13. Preserve existing API endpoints and JavaScript functionality unless the proposal explicitly requires a change.
                    14. Keep the interface mobile-friendly.
                    15. Preserve Blackwater's existing visual identity.
                    16. Do not add voice features.
                    17. Do not create fake controls that appear functional but are not connected.
                    18. Do not add unnecessary frontend libraries.
                    """);
        } else {
            prompt.append("""
                    
                    BACKEND RULES:
                    11. Preserve existing Spring Boot architecture.
                    12. Avoid unnecessary dependencies.
                    13. Preserve public APIs and existing behavior unless the proposal explicitly changes them.
                    14. Keep error handling explicit and safe.
                    """);
        }

        prompt.append("\n\nTARGET FILE:\n");
        prompt.append(filePath);

        prompt.append("\n\nDOMAIN:\n");
        prompt.append(nullSafe(proposal.domain()));

        prompt.append("\n\nFEATURE:\n");
        prompt.append(nullSafe(proposal.featureName()));

        prompt.append("\n\nPROPOSAL:\n");
        prompt.append(nullSafe(proposal.summary()));

        if (proposal.tests() != null && !proposal.tests().isEmpty()) {
            prompt.append("\n\nRECOMMENDED TESTS:\n");
            for (String test : proposal.tests()) {
                prompt.append("- ").append(test).append("\n");
            }
        }

        prompt.append("\n\nCURRENT FILE CONTENT:\n");
        prompt.append(currentSource);

        prompt.append("""
                
                \n\nNow produce the complete replacement file.
                """);

        return prompt.toString();
    }

    private String normalizeProjectPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String normalized = path
                .trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        if (!normalized.startsWith("src/main/")
                && !normalized.startsWith("src/test/")) {
            return null;
        }

        if (normalized.contains("..")
                || normalized.startsWith("/")
                || normalized.contains(":")
                || normalized.contains("\0")) {
            return null;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.contains("/.git/")
                || lower.contains("/target/")
                || lower.contains("/blackwater-sandbox/")
                || lower.contains("/blackwater-backups/")) {
            return null;
        }

        String fileName = Path.of(normalized)
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (!isSupportedExtension(fileName)) {
            return null;
        }

        return normalized;
    }

    private boolean isSupportedExtension(String fileName) {
        return fileName.endsWith(".java")
                || fileName.endsWith(".html")
                || fileName.endsWith(".css")
                || fileName.endsWith(".js")
                || fileName.endsWith(".json")
                || fileName.endsWith(".xml")
                || fileName.endsWith(".properties")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".md");
    }

    private boolean isUiFile(String filePath) {
        String lower = filePath.toLowerCase(Locale.ROOT);

        return lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".js")
                || lower.contains("/static/")
                || lower.contains("/templates/");
    }

    private String cleanGeneratedCode(String generated) {
        if (generated == null) {
            return "";
        }

        String result = generated.trim();

        if (result.startsWith("```")) {
            int firstNewLine = result.indexOf('\n');

            if (firstNewLine >= 0) {
                result = result.substring(firstNewLine + 1);
            }

            int closingFence = result.lastIndexOf("```");

            if (closingFence >= 0) {
                result = result.substring(0, closingFence);
            }
        }

        return result.trim();
    }

    private boolean isValidGeneratedContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        if (content.length() > 1_000_000) {
            return false;
        }

        String lower = content.toLowerCase(Locale.ROOT);

        return !lower.equals("null")
                && !lower.equals("undefined")
                && !lower.contains("i cannot")
                && !lower.contains("i can't generate")
                && !lower.contains("unable to generate");
    }

    private boolean looksDangerous(String content) {
        String lower = content.toLowerCase(Locale.ROOT);

        String[] blocked = {
                "runtime.getruntime",
                "processbuilder",
                "powershell",
                "cmd.exe",
                "rm -rf",
                "shutdown /",
                "format c:",
                "format /",
                "del /f",
                "mkfs.",
                "/etc/shadow",
                "private_key",
                "private key",
                "steal cookie",
                "cookie theft",
                "credential theft",
                "keylogger"
        };

        for (String pattern : blocked) {
            if (lower.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value
                .replaceAll("[^a-zA-Z0-9_-]", "-")
                .replaceAll("-+", "-")
                .substring(
                        0,
                        Math.min(
                                80,
                                value.replaceAll("[^a-zA-Z0-9_-]", "-")
                                        .replaceAll("-+", "-")
                                        .length()
                        )
                );
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null) {
            return "unknown error";
        }

        return e.getMessage()
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public record GenerationResult(
            String experimentId,
            boolean success,
            List<String> generatedFiles,
            List<String> errors
    ) {
    }
}
