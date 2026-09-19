package service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CodeAnalysisEngine {

    private static final Path PROJECT_ROOT =
            Path.of(".");

    private static final int MAX_FILE_SIZE = 300_000;

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(
                    "java",
                    "html",
                    "css",
                    "js",
                    "json",
                    "xml",
                    "properties",
                    "yml",
                    "yaml",
                    "md"
            );

    private static final Set<String> IGNORED_DIRECTORIES =
            Set.of(
                    ".git",
                    "target",
                    "blackwater-sandbox",
                    "node_modules",
                    ".idea",
                    ".vscode"
            );

    public ProjectAnalysis analyzeProject() {
        List<SourceFile> files =
                scanProject();

        long javaFiles =
                files.stream()
                        .filter(file ->
                                "java".equals(file.extension()))
                        .count();

        long uiFiles =
                files.stream()
                        .filter(file ->
                                isUiFile(file.extension()))
                        .count();

        long webFiles =
                files.stream()
                        .filter(file ->
                                isWebFile(file.extension()))
                        .count();

        long totalLines =
                files.stream()
                        .mapToLong(SourceFile::lines)
                        .sum();

        long totalBytes =
                files.stream()
                        .mapToLong(SourceFile::size)
                        .sum();

        return new ProjectAnalysis(
                files.size(),
                (int) javaFiles,
                (int) uiFiles,
                (int) webFiles,
                totalLines,
                totalBytes,
                files
        );
    }

    public List<SourceFile> findRelevantFiles(
            String feature,
            String domain
    ) {
        List<SourceFile> files =
                scanProject();

        String featureText =
                safe(feature)
                        .toLowerCase(Locale.ROOT);

        String domainText =
                safe(domain)
                        .toLowerCase(Locale.ROOT);

        Set<String> keywords =
                buildKeywords(
                        featureText,
                        domainText
                );

        return files.stream()
                .map(file ->
                        new ScoredFile(
                                file,
                                relevance(
                                        file,
                                        keywords,
                                        featureText,
                                        domainText
                                )
                        )
                )
                .filter(item ->
                        item.score() > 0)
                .sorted(
                        Comparator
                                .comparingDouble(
                                        ScoredFile::score
                                )
                                .reversed()
                )
                .limit(30)
                .map(ScoredFile::file)
                .collect(Collectors.toList());
    }

    public String readSource(
            String relativePath
    ) {
        if (relativePath == null
                || relativePath.isBlank()) {
            return "";
        }

        Path path =
                PROJECT_ROOT.resolve(relativePath)
                        .normalize();

        if (!path.startsWith(
                PROJECT_ROOT.toAbsolutePath()
        )) {
            return "";
        }

        try {
            if (!Files.exists(path)
                    || !Files.isRegularFile(path)) {
                return "";
            }

            if (Files.size(path)
                    > MAX_FILE_SIZE) {
                return "";
            }

            return Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );

        } catch (IOException ignored) {
            return "";
        }
    }

    public List<SourceFile> findFilesContaining(
            String text
    ) {
        String query =
                safe(text)
                        .toLowerCase(Locale.ROOT)
                        .trim();

        if (query.isBlank()) {
            return List.of();
        }

        return scanProject()
                .stream()
                .filter(file ->
                        safe(file.contentPreview())
                                .toLowerCase(Locale.ROOT)
                                .contains(query))
                .collect(Collectors.toList());
    }

    public List<SourceFile> getJavaFiles() {
        return scanProject()
                .stream()
                .filter(file ->
                        "java".equals(file.extension()))
                .collect(Collectors.toList());
    }

    public List<SourceFile> getUiFiles() {
        return scanProject()
                .stream()
                .filter(file ->
                        isUiFile(file.extension()))
                .collect(Collectors.toList());
    }

    public List<SourceFile> getWebFiles() {
        return scanProject()
                .stream()
                .filter(file ->
                        isWebFile(file.extension()))
                .collect(Collectors.toList());
    }

    public boolean isUiFile(
            String extension
    ) {
        if (extension == null) {
            return false;
        }

        return switch (
                extension.toLowerCase(Locale.ROOT)
        ) {
            case "html",
                 "css",
                 "js" -> true;

            default -> false;
        };
    }

    public boolean isWebFile(
            String extension
    ) {
        if (extension == null) {
            return false;
        }

        return switch (
                extension.toLowerCase(Locale.ROOT)
        ) {
            case "html",
                 "css",
                 "js",
                 "json",
                 "xml" -> true;

            default -> false;
        };
    }

    private List<SourceFile> scanProject() {
        List<SourceFile> result =
                new ArrayList<>();

        Path root =
                PROJECT_ROOT.toAbsolutePath()
                        .normalize();

        try (Stream<Path> stream =
                     Files.walk(root)) {

            stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            !isIgnored(path))
                    .forEach(path -> {
                        SourceFile file =
                                inspectFile(
                                        path,
                                        root
                                );

                        if (file != null) {
                            result.add(file);
                        }
                    });

        } catch (IOException ignored) {
        }

        return result;
    }

    private SourceFile inspectFile(
            Path path,
            Path root
    ) {
        String extension =
                getExtension(
                        path.getFileName()
                                .toString()
                );

        if (!SUPPORTED_EXTENSIONS
                .contains(extension)) {
            return null;
        }

        try {
            long size =
                    Files.size(path);

            if (size > MAX_FILE_SIZE) {
                return null;
            }

            String content =
                    Files.readString(
                            path,
                            StandardCharsets.UTF_8
                    );

            long lines =
                    content.isBlank()
                            ? 0
                            : content.lines().count();

            String relative =
                    root.relativize(path)
                            .toString()
                            .replace('\\', '/');

            String preview =
                    content.length() > 5000
                            ? content.substring(0, 5000)
                            : content;

            return new SourceFile(
                    relative,
                    extension,
                    size,
                    lines,
                    preview
            );

        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean isIgnored(
            Path path
    ) {
        for (Path part : path) {
            if (IGNORED_DIRECTORIES
                    .contains(
                            part.toString()
                    )) {
                return true;
            }
        }

        return false;
    }

    private double relevance(
            SourceFile file,
            Set<String> keywords,
            String feature,
            String domain
    ) {
        String path =
                safe(file.path())
                        .toLowerCase(Locale.ROOT);

        String preview =
                safe(file.contentPreview())
                        .toLowerCase(Locale.ROOT);

        String extension =
                safe(file.extension())
                        .toLowerCase(Locale.ROOT);

        double score = 0;

        for (String keyword : keywords) {
            if (keyword.isBlank()) {
                continue;
            }

            if (path.contains(keyword)) {
                score += 4;
            }

            if (preview.contains(keyword)) {
                score += 2;
            }
        }

        if (domain.contains("coding")) {
            score += 2;

            if ("java".equals(extension)) {
                score += 4;
            }

            if (isUiFile(extension)) {
                score += 4;
            }
        }

        if (containsUiKeyword(feature)) {
            if (isUiFile(extension)) {
                score += 10;
            }

            if (path.contains("static")) {
                score += 5;
            }

            if (path.contains("index")) {
                score += 5;
            }
        }

        if (containsBackendKeyword(feature)
                && "java".equals(extension)) {
            score += 8;
        }

        return score;
    }

    private Set<String> buildKeywords(
            String feature,
            String domain
    ) {
        Set<String> keywords =
                new HashSet<>();

        addTokens(
                keywords,
                feature
        );

        addTokens(
                keywords,
                domain
        );

        if (containsUiKeyword(feature)
                || domain.contains("coding")) {

            keywords.add("ui");
            keywords.add("html");
            keywords.add("css");
            keywords.add("javascript");
            keywords.add("static");
            keywords.add("style");
            keywords.add("button");
            keywords.add("panel");
            keywords.add("menu");
            keywords.add("dialog");
            keywords.add("modal");
            keywords.add("input");
            keywords.add("chat");
        }

        if (containsBackendKeyword(feature)) {
            keywords.add("controller");
            keywords.add("service");
            keywords.add("repository");
            keywords.add("api");
        }

        return keywords;
    }

    private void addTokens(
            Set<String> target,
            String value
    ) {
        for (String token :
                safe(value).split("[^a-zA-Z0-9_]+")) {

            if (token.length() >= 3) {
                target.add(
                        token.toLowerCase(
                                Locale.ROOT
                        )
                );
            }
        }
    }

    private boolean containsUiKeyword(
            String value
    ) {
        String text =
                safe(value)
                        .toLowerCase(Locale.ROOT);

        String[] keywords = {
                "ui",
                "interface",
                "frontend",
                "front-end",
                "html",
                "css",
                "javascript",
                "layout",
                "design",
                "button",
                "panel",
                "theme",
                "animation",
                "responsive",
                "mobile",
                "chat",
                "dashboard",
                "settings",
                "visual"
        };

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsBackendKeyword(
            String value
    ) {
        String text =
                safe(value)
                        .toLowerCase(Locale.ROOT);

        String[] keywords = {
                "backend",
                "api",
                "controller",
                "service",
                "database",
                "memory",
                "research",
                "knowledge",
                "reasoning"
        };

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String getExtension(
            String filename
    ) {
        int index =
                filename.lastIndexOf('.');

        if (index < 0
                || index == filename.length() - 1) {
            return "";
        }

        return filename
                .substring(index + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null
                ? ""
                : value;
    }

    private record ScoredFile(
            SourceFile file,
            double score
    ) {
    }

    public record SourceFile(
            String path,
            String extension,
            long size,
            long lines,
            String contentPreview
    ) {
    }

    public record ProjectAnalysis(
            int totalFiles,
            int javaFiles,
            int uiFiles,
            int webFiles,
            long totalLines,
            long totalBytes,
            List<SourceFile> files
    ) {

        public ProjectAnalysis(
                int totalFiles,
                int javaFiles,
                int webFiles,
                long totalLines,
                long totalBytes,
                List<SourceFile> files
        ) {
            this(
                    totalFiles,
                    javaFiles,
                    (int) files.stream()
                            .filter(file ->
                                    file.extension()
                                            .equals("html")
                                    || file.extension()
                                            .equals("css")
                                    || file.extension()
                                            .equals("js"))
                            .count(),
                    webFiles,
                    totalLines,
                    totalBytes,
                    files
            );
        }
    }
}
