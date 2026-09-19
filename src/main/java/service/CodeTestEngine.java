package service;

import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CodeTestEngine {

    private static final long MAX_SOURCE_SIZE = 1_000_000;

    private static final Pattern JAVA_TYPE =
            Pattern.compile(
                    "\\b(class|interface|enum|record)\\s+[A-Za-z_$][\\w$]*"
            );

    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile(
                    "\\b(function|const|let|var|class)\\b"
            );

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
                    null,
                    "Experiment ID is missing."
            );
        }

        if (!sandbox.exists(experimentId)) {

            return failed(
                    experimentId,
                    "Sandbox experiment does not exist."
            );
        }

        List<String> errors =
                new ArrayList<>();

        int totalTests = 0;
        int passedTests = 0;

        ImprovementVerifier.VerificationResult verification;

        try {

            verification =
                    verifier.verify(
                            experimentId
                    );

        } catch (Exception e) {

            return failed(
                    experimentId,
                    "Verification failed: "
                            + safeMessage(e)
            );
        }

        if (verification == null
                || !verification.passed()) {

            return failed(
                    experimentId,
                    "Safety verification failed."
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
                    "Could not inspect experiment: "
                            + safeMessage(e)
            );
        }

        if (inspection == null
                || !inspection.success()
                || inspection.files() == null
                || inspection.files().isEmpty()) {

            return failed(
                    experimentId,
                    "Experiment contains no generated files."
            );
        }

        Path experimentRoot =
                Path.of(
                        "blackwater-sandbox",
                        "experiments",
                        experimentId
                )
                        .toAbsolutePath()
                        .normalize();

        Set<String> testedFiles =
                new HashSet<>();

        for (String inspectedPath :
                inspection.files()) {

            String file =
                    toProjectRelativePath(
                            experimentRoot,
                            inspectedPath
                    );

            if (!isProjectFile(file)) {
                continue;
            }

            if (!testedFiles.add(file)) {
                continue;
            }

            String content;

            try {

                content =
                        sandbox.readFile(
                                experimentId,
                                file
                        );

            } catch (Exception e) {

                errors.add(
                        "Could not read generated file: "
                                + file
                );

                continue;
            }

            totalTests++;

            if (basicContentTest(
                    file,
                    content
            )) {

                passedTests++;

            } else {

                errors.add(
                        "Basic content test failed: "
                                + file
                );
            }

            totalTests++;

            if (syntaxStructureTest(
                    file,
                    content
            )) {

                passedTests++;

            } else {

                errors.add(
                        "Syntax/structure test failed: "
                                + file
                );
            }

            totalTests++;

            if (qualityStructureTest(
                    file,
                    content
            )) {

                passedTests++;

            } else {

                errors.add(
                        "Quality structure test failed: "
                                + file
                );
            }

            totalTests++;

            if (safetyContentTest(
                    content
            )) {

                passedTests++;

            } else {

                errors.add(
                        "Safety content test failed: "
                                + file
                );
            }
        }

        List<String> javaFiles =
                testedFiles.stream()
                        .filter(
                                this::isJava
                        )
                        .toList();

        if (!javaFiles.isEmpty()) {

            totalTests++;

            JavaCompileResult compileResult =
                    compileJavaFiles(
                            experimentId,
                            javaFiles
                    );

            if (compileResult.success()) {

                passedTests++;

            } else {

                errors.addAll(
                        compileResult.errors()
                );
            }
        }

        boolean success =
                totalTests > 0
                        && passedTests == totalTests
                        && errors.isEmpty();

        double score =
                totalTests == 0
                        ? 0.0
                        : (double) passedTests
                        / totalTests;

        String summary =
                "CODE_TEST"
                        + System.lineSeparator()
                        + "Passed "
                        + passedTests
                        + " / "
                        + totalTests
                        + " tests."
                        + System.lineSeparator()
                        + "Score: "
                        + String.format(
                                Locale.ROOT,
                                "%.3f",
                                score
                        );

        try {

            sandbox.recordResult(
                    experimentId,
                    success,
                    summary
            );

        } catch (Exception ignored) {
        }

        return new TestResult(
                experimentId,
                success,
                score,
                List.copyOf(errors)
        );
    }

    private String toProjectRelativePath(
            Path experimentRoot,
            String inspectedPath
    ) {

        if (inspectedPath == null
                || inspectedPath.isBlank()) {

            return "";
        }

        try {

            Path path =
                    Path.of(
                            inspectedPath
                    )
                            .toAbsolutePath()
                            .normalize();

            if (!path.startsWith(
                    experimentRoot
            )) {

                return "";
            }

            Path relative =
                    experimentRoot.relativize(
                            path
                    );

            return normalizePath(
                    relative.toString()
            );

        } catch (Exception e) {

            return "";
        }
    }

    private JavaCompileResult compileJavaFiles(
            String experimentId,
            List<String> javaFiles
    ) {

        JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {

            return new JavaCompileResult(
                    false,
                    List.of(
                            "Java compiler is unavailable. "
                                    + "Blackwater must run on a JDK."
                    )
            );
        }

        Path experimentRoot =
                Path.of(
                        "blackwater-sandbox",
                        "experiments",
                        experimentId
                )
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isDirectory(
                experimentRoot
        )) {

            return new JavaCompileResult(
                    false,
                    List.of(
                            "Sandbox experiment directory not found."
                    )
            );
        }

        Path compileOutput =
                experimentRoot
                        .resolve(
                                ".compile-output"
                        )
                        .normalize();

        if (!compileOutput.startsWith(
                experimentRoot
        )) {

            return new JavaCompileResult(
                    false,
                    List.of(
                            "Unsafe compilation output path."
                    )
            );
        }

        try {

            Files.createDirectories(
                    compileOutput
            );

        } catch (IOException e) {

            return new JavaCompileResult(
                    false,
                    List.of(
                            "Could not create compilation directory: "
                                    + safeMessage(e)
                    )
            );
        }

        List<Path> sourcePaths =
                new ArrayList<>();

        for (String file :
                javaFiles) {

            if (!isJava(file)
                    || !isProjectFile(file)) {

                return new JavaCompileResult(
                        false,
                        List.of(
                                "Unsupported Java source path: "
                                        + file
                        )
                );
            }

            Path source =
                    experimentRoot
                            .resolve(file)
                            .normalize();

            if (!source.startsWith(
                    experimentRoot
            )) {

                return new JavaCompileResult(
                        false,
                        List.of(
                                "Unsafe Java source path: "
                                        + file
                        )
                );
            }

            if (!Files.isRegularFile(
                    source
            )) {

                return new JavaCompileResult(
                        false,
                        List.of(
                                "Java source file does not exist: "
                                        + file
                        )
                );
            }

            sourcePaths.add(
                    source
            );
        }

        if (sourcePaths.isEmpty()) {

            return new JavaCompileResult(
                    true,
                    List.of()
            );
        }

        DiagnosticCollector<JavaFileObject>
                diagnostics =
                new DiagnosticCollector<>();

        try (
                StandardJavaFileManager fileManager =
                        compiler.getStandardFileManager(
                                diagnostics,
                                Locale.ROOT,
                                StandardCharsets.UTF_8
                        )
        ) {

            Iterable<? extends JavaFileObject>
                    units =
                    fileManager
                            .getJavaFileObjectsFromFiles(
                                    sourcePaths
                                            .stream()
                                            .map(
                                                    Path::toFile
                                            )
                                            .toList()
                            );

            List<String> options =
                    List.of(
                            "-proc:none",
                            "-encoding",
                            "UTF-8",
                            "-Xlint:none",
                            "-d",
                            compileOutput.toString()
                    );

            JavaCompiler.CompilationTask task =
                    compiler.getTask(
                            null,
                            fileManager,
                            diagnostics,
                            options,
                            null,
                            units
                    );

            boolean success =
                    Boolean.TRUE.equals(
                            task.call()
                    );

            if (success) {

                return new JavaCompileResult(
                        true,
                        List.of()
                );
            }

            List<String> compileErrors =
                    new ArrayList<>();

            for (
                    Diagnostic<? extends JavaFileObject>
                            diagnostic
                    : diagnostics.getDiagnostics()
            ) {

                if (diagnostic.getKind()
                        != Diagnostic.Kind.ERROR) {

                    continue;
                }

                String sourceName =
                        diagnostic.getSource() == null
                                ? "unknown"
                                : Path.of(
                                        diagnostic
                                                .getSource()
                                                .getName()
                                )
                                .getFileName()
                                .toString();

                compileErrors.add(
                        "Java compile error in "
                                + sourceName
                                + " at line "
                                + diagnostic.getLineNumber()
                                + ": "
                                + diagnostic.getMessage(
                                        Locale.ROOT
                                )
                );
            }

            if (compileErrors.isEmpty()) {

                compileErrors.add(
                        "Java compilation failed."
                );
            }

            return new JavaCompileResult(
                    false,
                    List.copyOf(
                            compileErrors
                    )
            );

        } catch (Exception e) {

            return new JavaCompileResult(
                    false,
                    List.of(
                            "Java compilation failed: "
                                    + safeMessage(e)
                    )
            );
        }
    }

    private boolean basicContentTest(
            String file,
            String content
    ) {

        if (content == null
                || content.isBlank()) {

            return false;
        }

        if (content.length()
                > MAX_SOURCE_SIZE) {

            return false;
        }

        if (!isSupportedFile(file)) {

            return false;
        }

        String lower =
                content.toLowerCase(
                        Locale.ROOT
                );

        String[] unfinishedMarkers = {
                "todo: implement",
                "insert code here",
                "your code here",
                "implementation omitted",
                "code omitted",
                "implementation goes here",
                "replace this with",
                "coming soon"
        };

        for (String marker :
                unfinishedMarkers) {

            if (lower.contains(marker)) {
                return false;
            }
        }

        return true;
    }

    private boolean syntaxStructureTest(
            String file,
            String content
    ) {

        if (content == null
                || content.isBlank()) {

            return false;
        }

        if (isJava(file)) {

            return balancedIgnoringStrings(
                    content,
                    '{',
                    '}'
            )
                    && balancedIgnoringStrings(
                    content,
                    '(',
                    ')'
            )
                    && balancedIgnoringStrings(
                    content,
                    '[',
                    ']'
            )
                    && !containsBrokenJava(
                    content
            );
        }

        if (isJavaScript(file)) {

            return balancedIgnoringStrings(
                    content,
                    '{',
                    '}'
            )
                    && balancedIgnoringStrings(
                    content,
                    '(',
                    ')'
            )
                    && balancedIgnoringStrings(
                    content,
                    '[',
                    ']'
            );
        }

        if (isCss(file)) {

            return balancedIgnoringStrings(
                    content,
                    '{',
                    '}'
            );
        }

        if (isHtml(file)) {

            return balancedHtml(
                    content
            );
        }

        return true;
    }

    private boolean qualityStructureTest(
            String file,
            String content
    ) {

        if (content == null
                || content.isBlank()) {

            return false;
        }

        if (isJava(file)) {
            return javaQualityTest(content);
        }

        if (isHtml(file)) {
            return htmlQualityTest(content);
        }

        if (isCss(file)) {
            return cssQualityTest(content);
        }

        if (isJavaScript(file)) {
            return javascriptQualityTest(content);
        }

        return true;
    }

    private boolean javaQualityTest(
            String content
    ) {

        if (!JAVA_TYPE.matcher(
                content
        ).find()) {

            return false;
        }

        if (content.contains(
                "System.out.println"
        )
                && content.length() > 500) {

            return false;
        }

        if (content.contains(
                "catch (Exception e)"
        )
                && content.contains(
                "e.printStackTrace()"
        )) {

            return false;
        }

        return !containsBrokenJava(
                content
        );
    }

    private boolean htmlQualityTest(
            String content
    ) {

        String lower =
                content.toLowerCase(
                        Locale.ROOT
                );

        boolean hasDocument =
                lower.contains(
                        "<!doctype html"
                )
                        || lower.contains(
                        "<html"
                );

        if (!hasDocument) {
            return false;
        }

        if (!lower.contains(
                "<body"
        )) {

            return false;
        }

        if (lower.contains(
                "<script src=\"\""
        )) {

            return false;
        }

        if (lower.contains(
                "href=\"javascript:"
        )) {

            return false;
        }

        return true;
    }

    private boolean cssQualityTest(
            String content
    ) {

        if (!balancedIgnoringStrings(
                content,
                '{',
                '}'
        )) {

            return false;
        }

        String compact =
                content.replaceAll(
                        "\\s+",
                        ""
                );

        if (compact.contains("{}")) {
            return false;
        }

        if (compact.contains(
                "color:;"
        )
                || compact.contains(
                "width:;"
        )
                || compact.contains(
                "height:;"
        )) {

            return false;
        }

        return true;
    }

    private boolean javascriptQualityTest(
            String content
    ) {

        if (!balancedIgnoringStrings(
                content,
                '{',
                '}'
        )
                || !balancedIgnoringStrings(
                content,
                '(',
                ')'
        )
                || !balancedIgnoringStrings(
                content,
                '[',
                ']'
        )) {

            return false;
        }

        String lower =
                content.toLowerCase(
                        Locale.ROOT
                );

        if (lower.contains(
                "debugger;"
        )) {

            return false;
        }

        if (lower.contains(
                "javascript:javascript:"
        )) {

            return false;
        }

        return FUNCTION_PATTERN
                .matcher(content)
                .find()
                || content.contains("=>");
    }

    private boolean safetyContentTest(
            String content
    ) {

        if (content == null) {
            return false;
        }

        String lower =
                content.toLowerCase(
                        Locale.ROOT
                );

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
                "keylogger",
                "steal cookie",
                "credential theft",
                "disable security",
                "bypass antivirus"
        };

        for (String blockedValue :
                blocked) {

            if (lower.contains(
                    blockedValue
            )) {

                return false;
            }
        }

        return true;
    }

    private boolean containsBrokenJava(
            String content
    ) {

        String lower =
                content.toLowerCase(
                        Locale.ROOT
                );

        return lower.contains(
                "throw new unsupportedoperationexception"
        )
                || lower.contains(
                "todo implement"
        )
                || lower.contains(
                "implementation omitted"
        )
                || lower.contains(
                "return null; // todo"
        );
    }

    private boolean balancedIgnoringStrings(
            String content,
            char opening,
            char closing
    ) {

        int depth = 0;

        boolean inDoubleString = false;
        boolean inSingleString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int i = 0;
             i < content.length();
             i++) {

            char c =
                    content.charAt(i);

            char next =
                    i + 1 < content.length()
                            ? content.charAt(i + 1)
                            : '\0';

            if (inLineComment) {

                if (c == '\n'
                        || c == '\r') {

                    inLineComment = false;
                }

                continue;
            }

            if (inBlockComment) {

                if (c == '*'
                        && next == '/') {

                    inBlockComment = false;
                    i++;
                }

                continue;
            }

            if (escaped) {

                escaped = false;
                continue;
            }

            if ((inDoubleString
                    || inSingleString)
                    && c == '\\') {

                escaped = true;
                continue;
            }

            if (!inSingleString
                    && !inDoubleString
                    && c == '/'
                    && next == '/') {

                inLineComment = true;
                i++;
                continue;
            }

            if (!inSingleString
                    && !inDoubleString
                    && c == '/'
                    && next == '*') {

                inBlockComment = true;
                i++;
                continue;
            }

            if (!inSingleString
                    && c == '"') {

                inDoubleString =
                        !inDoubleString;

                continue;
            }

            if (!inDoubleString
                    && c == '\'') {

                inSingleString =
                        !inSingleString;

                continue;
            }

            if (inDoubleString
                    || inSingleString) {

                continue;
            }

            if (c == opening) {

                depth++;

            } else if (c == closing) {

                depth--;

                if (depth < 0) {
                    return false;
                }
            }
        }

        return depth == 0
                && !inDoubleString
                && !inSingleString
                && !inBlockComment;
    }

    private boolean balancedHtml(
            String content
    ) {

        String lower =
                content.toLowerCase(
                        Locale.ROOT
                );

        String[] tags = {
                "html",
                "head",
                "body",
                "main",
                "section",
                "article",
                "header",
                "footer",
                "nav",
                "div",
                "script",
                "style"
        };

        for (String tag :
                tags) {

            int open =
                    countOpeningTags(
                            lower,
                            tag
                    );

            int close =
                    countOccurrences(
                            lower,
                            "</" + tag + ">"
                    );

            if (open != close) {
                return false;
            }
        }

        return true;
    }

    private int countOpeningTags(
            String value,
            String tag
    ) {

        int count = 0;
        int index = 0;

        String target =
                "<" + tag;

        while ((index =
                value.indexOf(
                        target,
                        index
                )) >= 0) {

            int after =
                    index + target.length();

            if (after >= value.length()) {

                count++;
                break;
            }

            char next =
                    value.charAt(after);

            if (Character.isWhitespace(next)
                    || next == '>'
                    || next == '/') {

                count++;
            }

            index = after;
        }

        return count;
    }

    private int countOccurrences(
            String value,
            String target
    ) {

        int count = 0;
        int index = 0;

        while ((index =
                value.indexOf(
                        target,
                        index
                )) >= 0) {

            count++;
            index += target.length();
        }

        return count;
    }

    private boolean isProjectFile(
            String file
    ) {

        if (file == null) {
            return false;
        }

        return file.startsWith(
                "src/main/"
        )
                || file.startsWith(
                "src/test/"
        );
    }

    private boolean isSupportedFile(
            String file
    ) {

        if (file == null) {
            return false;
        }

        String lower =
                file.toLowerCase(
                        Locale.ROOT
                );

        return lower.endsWith(".java")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".js")
                || lower.endsWith(".json")
                || lower.endsWith(".xml")
                || lower.endsWith(".properties")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".md");
    }

    private boolean isJava(
            String file
    ) {

        return file != null
                && file.endsWith(".java");
    }

    private boolean isHtml(
            String file
    ) {

        return file != null
                && file.endsWith(".html");
    }

    private boolean isCss(
            String file
    ) {

        return file != null
                && file.endsWith(".css");
    }

    private boolean isJavaScript(
            String file
    ) {

        return file != null
                && file.endsWith(".js");
    }

    private String normalizePath(
            String file
    ) {

        if (file == null) {
            return "";
        }

        String normalized =
                file
                        .replace(
                                '\\',
                                '/'
                        )
                        .replaceAll(
                                "^/+",
                                ""
                        );

        while (normalized.startsWith(
                "./"
        )) {

            normalized =
                    normalized.substring(
                            2
                    );
        }

        return normalized;
    }

    private TestResult failed(
            String experimentId,
            String error
    ) {

        return new TestResult(
                experimentId,
                false,
                0.0,
                List.of(error)
        );
    }

    private String safeMessage(
            Exception e
    ) {

        if (e == null
                || e.getMessage() == null) {

            return "unknown error";
        }

        return e.getMessage()
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    public record TestResult(
            String experimentId,
            boolean success,
            double score,
            List<String> errors
    ) {
    }

    private record JavaCompileResult(
            boolean success,
            List<String> errors
    ) {
    }
}
