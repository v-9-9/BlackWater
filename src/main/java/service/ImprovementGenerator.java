package service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImprovementGenerator {

    private final AIService aiService;
    private final CodeAnalysisEngine codeAnalysisEngine;

    public ImprovementGenerator(
            AIService aiService,
            CodeAnalysisEngine codeAnalysisEngine
    ) {
        this.aiService = aiService;
        this.codeAnalysisEngine =
                codeAnalysisEngine;
    }

    public synchronized ImprovementProposal generate(
            String domain,
            SelfImprovementFeatureBank.Feature feature
    ) {

        if (feature == null) {

            return new ImprovementProposal(
                    domain,
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    "No feature supplied."
            );
        }

        List<CodeAnalysisEngine.SourceFile> relevantFiles =
                codeAnalysisEngine.findRelevantFiles(
                        feature.name(),
                        domain
                );

        String projectContext =
                buildProjectContext(
                        relevantFiles
                );

        String prompt =
                buildPrompt(
                        domain,
                        feature,
                        projectContext
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

            return new ImprovementProposal(
                    domain,
                    feature.name(),
                    "",
                    extractPaths(
                            relevantFiles
                    ),
                    List.of(),
                    List.of(),
                    false,
                    "AI generation failed: "
                            + e.getMessage()
            );
        }

        if (response == null
                || response.isBlank()) {

            return new ImprovementProposal(
                    domain,
                    feature.name(),
                    "",
                    extractPaths(
                            relevantFiles
                    ),
                    List.of(),
                    List.of(),
                    false,
                    "No improvement proposal generated."
            );
        }

        ParsedProposal parsed =
                parseResponse(
                        response
                );

        return new ImprovementProposal(
                domain,
                feature.name(),
                parsed.summary(),
                extractPaths(
                        relevantFiles
                ),
                parsed.files(),
                parsed.tests(),
                true,
                response
        );
    }

    private String buildPrompt(
            String domain,
            SelfImprovementFeatureBank.Feature feature,
            String projectContext
    ) {

        return """
                You are the software architecture and improvement
                planner for Blackwater.

                Do NOT directly modify files.

                Analyze the requested capability and create a safe
                implementation proposal.

                Domain:
                %s

                Capability:
                %s

                Capability description:
                %s

                Priority:
                %d

                Relevant project files:
                %s

                Produce a technical proposal containing:

                SUMMARY:
                A concise description of the improvement.

                FILES:
                List the project files that should be created,
                replaced or modified.

                TESTS:
                List automated tests that should verify the change.

                RULES:
                - Do not invent files that are unrelated to the feature.
                - Prefer small, isolated changes.
                - Preserve existing functionality.
                - Identify frontend and backend changes separately.
                - Mention dependencies if required.
                - Mention security risks.
                - Do not claim that an implementation works before testing.
                """.formatted(
                domain,
                feature.name(),
                feature.description(),
                feature.priority(),
                projectContext
        ).trim();
    }

    private String buildProjectContext(
            List<CodeAnalysisEngine.SourceFile> files
    ) {

        if (files == null
                || files.isEmpty()) {

            return "No strongly relevant files were identified.";
        }

        StringBuilder context =
                new StringBuilder();

        int limit =
                Math.min(
                        files.size(),
                        20
                );

        for (int i = 0;
             i < limit;
             i++) {

            CodeAnalysisEngine.SourceFile file =
                    files.get(i);

            context.append(
                    "FILE: "
            )
            .append(
                    file.path()
            )
            .append(
                    System.lineSeparator()
            )
            .append(
                    "TYPE: "
            )
            .append(
                    file.extension()
            )
            .append(
                    System.lineSeparator()
            )
            .append(
                    "LINES: "
            )
            .append(
                    file.lines()
            )
            .append(
                    System.lineSeparator()
            )
            .append(
                    "PREVIEW:"
            )
            .append(
                    System.lineSeparator()
            )
            .append(
                    file.contentPreview()
            )
            .append(
                    System.lineSeparator()
            )
            .append(
                    System.lineSeparator()
            );
        }

        return context.toString().trim();
    }

    private List<String> extractPaths(
            List<CodeAnalysisEngine.SourceFile> files
    ) {

        if (files == null
                || files.isEmpty()) {

            return List.of();
        }

        return files.stream()
                .map(
                        CodeAnalysisEngine.SourceFile::path
                )
                .toList();
    }

    private ParsedProposal parseResponse(
            String response
    ) {

        String summary =
                extractSection(
                        response,
                        "SUMMARY:",
                        "FILES:"
                );

        String filesSection =
                extractSection(
                        response,
                        "FILES:",
                        "TESTS:"
                );

        String testsSection =
                extractSection(
                        response,
                        "TESTS:",
                        "RULES:"
                );

        List<String> files =
                parseList(
                        filesSection
                );

        List<String> tests =
                parseList(
                        testsSection
                );

        if (summary.isBlank()) {

            summary =
                    "Generated improvement proposal.";
        }

        return new ParsedProposal(
                summary,
                files,
                tests
        );
    }

    private String extractSection(
            String text,
            String startMarker,
            String endMarker
    ) {

        int start =
                text.indexOf(
                        startMarker
                );

        if (start < 0) {
            return "";
        }

        start +=
                startMarker.length();

        int end =
                text.indexOf(
                        endMarker,
                        start
                );

        if (end < 0) {
            end = text.length();
        }

        return text
                .substring(
                        start,
                        end
                )
                .trim();
    }

    private List<String> parseList(
            String section
    ) {

        if (section == null
                || section.isBlank()) {

            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        String[] lines =
                section.split(
                        "\\R"
                );

        for (String line :
                lines) {

            String clean =
                    line.trim()
                            .replaceFirst(
                                    "^[-*•]\\s*",
                                    ""
                            )
                            .replaceFirst(
                                    "^\\d+[.)]\\s*",
                                    ""
                            )
                            .trim();

            if (!clean.isBlank()) {

                result.add(
                        clean
                );
            }
        }

        return result;
    }

    private record ParsedProposal(
            String summary,
            List<String> files,
            List<String> tests
    ) {
    }

    public record ImprovementProposal(
            String domain,
            String featureName,
            String summary,
            List<String> relevantFiles,
            List<String> proposedFiles,
            List<String> tests,
            boolean generated,
            String rawProposal
    ) {
    }
}
