package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CodeAnalysisEngine {

    private static final Path PROJECT_ROOT =
            Path.of("src/main");

    private static final int MAX_FILE_SIZE =
            300_000;

    private static final List<String> IGNORED_DIRECTORIES =
            List.of(
                    "target",
                    ".git",
                    "blackwater-sandbox",
                    "node_modules"
            );

    public synchronized ProjectAnalysis analyzeProject() {

        List<SourceFile> files =
                new ArrayList<>();

        if (!Files.exists(PROJECT_ROOT)) {

            return new ProjectAnalysis(
                    0,
                    0,
                    0,
                    0,
                    files
            );
        }

        try {

            Files.walk(PROJECT_ROOT)
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            this::isSupportedFile
                    )
                    .filter(
                            path ->
                                    !isIgnored(path)
                    )
                    .forEach(
                            path ->
                                    analyzeFile(
                                            path,
                                            files
                                    )
                    );

        } catch (IOException ignored) {
        }

        int javaFiles = 0;
        int webFiles = 0;
        int configFiles = 0;
        int totalLines = 0;

        for (SourceFile file : files) {

            totalLines +=
                    file.lines();

            String extension =
                    extension(
                            file.path()
                    );

            if ("java".equals(extension)) {

                javaFiles++;

            } else if (
                    "html".equals(extension)
                            || "css".equals(extension)
                            || "js".equals(extension)
            ) {

                webFiles++;

            } else {

                configFiles++;
            }
        }

        return new ProjectAnalysis(
                files.size(),
                javaFiles,
                webFiles,
                configFiles,
                totalLines,
                files
        );
    }

    public synchronized List<SourceFile> findRelevantFiles(
            String feature,
            String domain
    ) {

        ProjectAnalysis analysis =
                analyzeProject();

        if (analysis.files().isEmpty()) {
            return List.of();
        }

        String featureText =
                normalize(
                        feature
                );

        String domainText =
                normalize(
                        domain
                );

        List<String> keywords =
                buildKeywords(
                        featureText,
                        domainText
                );

        List<ScoredFile> scored =
                new ArrayList<>();

        for (SourceFile file :
                analysis.files()) {

            int score =
                    calculateRelevance(
                            file,
                            keywords
                    );

            if (score > 0) {

                scored.add(
                        new ScoredFile(
                                file,
                                score
                        )
                );
            }
        }

        scored.sort(
                Comparator
                        .comparingInt(
                                ScoredFile::score
                        )
                        .reversed()
                        .thenComparing(
                                item ->
                                        item.file()
                                                .path()
                        )
        );

        return scored.stream()
                .limit(30)
                .map(
                        ScoredFile::file
                )
                .toList();
    }

    public synchronized String readSource(
            String relativePath
    ) {

        if (relativePath == null
                || relativePath.isBlank()) {

            return "";
        }

        Path path =
                Path.of(
                        relativePath
                ).normalize();

        if (!path.startsWith(
                PROJECT_ROOT.normalize()
        )) {

            return "";
        }

        if (!Files.exists(path)
                || !Files.isRegularFile(path)) {

            return "";
        }

        try {

            long size =
                    Files.size(path);

            if (size > MAX_FILE_SIZE) {
                return "";
            }

            return Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {

            return "";
        }
    }

    public synchronized List<String> findFilesContaining(
            String text
    ) {

        if (text == null
                || text.isBlank()) {

            return List.of();
        }

        String search =
                text.toLowerCase(
                        Locale.ROOT
                );

        ProjectAnalysis analysis =
                analyzeProject();

        List<String> result =
                new ArrayList<>();

        for (SourceFile file :
                analysis.files()) {

            if (file.contentPreview()
                    .toLowerCase(
                            Locale.ROOT
                    )
                    .contains(search)) {

                result.add(
                        file.path()
                );
            }
        }

        return result;
    }

    public synchronized List<String> getJavaFiles() {

        return analyzeProject()
                .files()
                .stream()
                .filter(
                        file ->
                                "java".equals(
                                        extension(
                                                file.path()
                                        )
                                )
                )
                .map(
                        SourceFile::path
                )
                .toList();
    }

    private void analyzeFile(
            Path path,
            List<SourceFile> result
    ) {

        try {

            long size =
                    Files.size(path);

            if (size > MAX_FILE_SIZE) {
                return;
            }

            String content =
                    Files.readString(
                            path,
                            StandardCharsets.UTF_8
                    );

            int lines =
                    content.isBlank()
                            ? 0
                            : content.split(
                                    "\\R",
                                    -1
                            ).length;

            String preview =
                    content.length() > 8000
                            ? content.substring(
                                    0,
                                    8000
                            )
                            : content;

            result.add(
                    new SourceFile(
                            path.toString(),
                            extension(
                                    path.toString()
                            ),
                            size,
                            lines,
                            preview
                    )
            );

        } catch (IOException ignored) {
        }
    }

    private int calculateRelevance(
            SourceFile file,
            List<String> keywords
    ) {

        String path =
                file.path()
                        .toLowerCase(
                                Locale.ROOT
                        );

        String content =
                file.contentPreview()
                        .toLowerCase(
                                Locale.ROOT
                        );

        int score = 0;

        for (String keyword :
                keywords) {

            if (keyword.isBlank()) {
                continue;
            }

            if (path.contains(keyword)) {
                score += 10;
            }

            if (content.contains(keyword)) {
                score += 3;
            }
        }

        String extension =
                extension(
                        file.path()
                );

        if ("java".equals(extension)) {
            score += 2;
        }

        if ("html".equals(extension)
                || "css".equals(extension)
                || "js".equals(extension)) {

            if (keywords.stream().anyMatch(
                    keyword ->
                            keyword.contains("ui")
                                    || keyword.contains("voice")
                                    || keyword.contains("image")
                                    || keyword.contains("file")
            )) {

                score += 5;
            }
        }

        return score;
    }

    private List<String> buildKeywords(
            String feature,
            String domain
    ) {

        List<String> keywords =
                new ArrayList<>();

        addWords(
                keywords,
                feature
        );

        addWords(
                keywords,
                domain
        );

        switch (domain) {

            case "coding" -> {

                keywords.add("service");
                keywords.add("controller");
                keywords.add("engine");
                keywords.add("provider");
            }

            case "reasoning" -> {

                keywords.add("ai");
                keywords.add("prompt");
                keywords.add("response");
            }

            case "research" -> {

                keywords.add("web");
                keywords.add("knowledge");
                keywords.add("learning");
            }

            case "memory" -> {

                keywords.add("memory");
                keywords.add("conversation");
                keywords.add("storage");
            }

            case "knowledge" -> {

                keywords.add("knowledge");
                keywords.add("evaluator");
            }

            default -> {
            }
        }

        return keywords;
    }

    private void addWords(
            List<String> result,
            String text
    ) {

        if (text == null
                || text.isBlank()) {

            return;
        }

        String[] words =
                text.split("\\s+");

        for (String word :
                words) {

            String clean =
                    word
                            .replaceAll(
                                    "[^a-zA-Z0-9]",
                                    ""
                            )
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (clean.length() >= 3) {

                result.add(clean);
            }
        }
    }

    private boolean isSupportedFile(
            Path path
    ) {

        String extension =
                extension(
                        path.toString()
                );

        return switch (extension) {

            case "java",
                 "html",
                 "css",
                 "js",
                 "json",
                 "xml",
                 "properties",
                 "yml",
                 "yaml",
                 "md" ->
                    true;

            default ->
                    false;
        };
    }

    private boolean isIgnored(
            Path path
    ) {

        String value =
                path.toString()
                        .replace(
                                '\\',
                                '/'
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        for (String ignored :
                IGNORED_DIRECTORIES) {

            String marker =
                    "/" + ignored + "/";

            if (value.contains(marker)) {
                return true;
            }
        }

        return false;
    }

    private String extension(
            String path
    ) {

        if (path == null) {
            return "";
        }

        int index =
                path.lastIndexOf('.');

        if (index < 0
                || index == path.length() - 1) {

            return "";
        }

        return path.substring(
                index + 1
        ).toLowerCase(
                Locale.ROOT
        );
    }

    private String normalize(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^a-zA-Z0-9]+",
                        " "
                )
                .trim();
    }

    public record ProjectAnalysis(
            int totalFiles,
            int javaFiles,
            int webFiles,
            int configFiles,
            int totalLines,
            List<SourceFile> files
    ) {

        public ProjectAnalysis(
                int totalFiles,
                int javaFiles,
                int webFiles,
                int configFiles,
                List<SourceFile> files
        ) {

            this(
                    totalFiles,
                    javaFiles,
                    webFiles,
                    configFiles,
                    0,
                    files
            );
        }
    }

    public record SourceFile(
            String path,
            String extension,
            long size,
            int lines,
            String contentPreview
    ) {
    }

    private record ScoredFile(
            SourceFile file,
            int score
    ) {
    }
}
