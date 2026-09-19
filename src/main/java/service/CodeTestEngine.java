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
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public TestResult test(String experimentId) {
        if (experimentId == null || experimentId.isBlank()) {
            return new TestResult(
                    null,
                    false,
                    0.0,
                    List.of("Experiment ID is missing.")
            );
        }

        if (!sandbox.exists(experimentId)) {
            return new TestResult(
                    experimentId,
                    false,
                    0.0,
                    List.of("Sandbox experiment does not exist.")
            );
        }

        List<String> errors = new ArrayList<>();
        int totalTests = 0;
        int passedTests = 0;

        ImprovementVerifier.VerificationResult verification;

        try {
            verification = verifier.verify(experimentId);
        } catch (Exception e) {
            return new TestResult(
                    experimentId,
                    false,
                    0.0,
                    List.of("Verification failed: " + safeMessage(e))
            );
        }

        if (verification == null || !verification.safe()) {
            return new TestResult(
                    experimentId,
                    false,
                    0.0,
                    List.of("Safety verification failed.")
            );
        }

        Map<String, String> files;

        try {
            files = sandbox.inspect(experimentId);
        } catch (Exception e) {
            return new TestResult(
                    experimentId,
                    false,
                    0.0,
                    List.of(
                            "Could not inspect experiment: "
                                    + safeMessage(e)
                    )
            );
        }

        if (files == null || files.isEmpty()) {
            return new TestResult(
                    experimentId,
                    false,
                    0.0,
                    List.of("Experiment contains no generated files.")
            );
        }

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String file = entry.getKey();
            String content = entry.getValue();

            if (!isProjectFile(file)) {
                continue;
            }

            totalTests++;

            if (basicContentTest(file, content)) {
                passedTests++;
            } else {
                errors.add(
                        "Basic content test failed: " + file
                );
            }

            totalTests++;

            if (syntaxStructureTest(file, content)) {
                passedTests++;
            } else {
                errors.add(
                        "Structure test failed: " + file
                );
            }

            if (isJava(file)) {
                totalTests++;

                if (javaStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add(
                            "Java structure test failed: " + file
                    );
                }
            }

            if (isHtml(file)) {
                totalTests++;

                if (htmlStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add(
                            "HTML structure test failed: " + file
                    );
                }
            }

            if (isCss(file)) {
                totalTests++;

                if (cssStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add(
                            "CSS structure test failed: " + file
                    );
                }
            }

            if (isJavaScript(file)) {
                totalTests++;

                if (javascriptStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add(
                            "JavaScript structure test failed: " + file
                    );
                }
            }

            totalTests++;

            if (safetyContentTest(content)) {
                passedTests++;
            } else {
                errors.add(
                        "Safety content test failed: " + file
                );
            }
        }

        /*
         * Compile generated Java files against the existing project.
         */
        List<String> javaFiles =
                files.keySet()
                        .stream()
                        .filter(this::isJava)
                        .filter(this::isProjectFile)
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
                        : (double) passedTests / totalTests;

        String summary =
                "Passed " +
                        passedTests +
                        " / " +
                        totalTests +
                        " tests. Score: " +
                        String.format(
                                Locale.ROOT,
                                "%.3f",
                                score
                        );

        try {
            sandbox.recordResult(
                    experimentId,
                    "CODE_TEST",
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
                                    + "The application must run on a JDK."
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

        if (!Files.isDirectory(experimentRoot)) {
            return new JavaCompileResult(
                    false,
                    List.of(
                            "Sandbox experiment directory not found."
                    )
            );
        }

        Path compileOutput =
                experimentRoot
                        .resolve(".compile-output")
                        .normalize();

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

        List<Path> sourcePaths = new ArrayList<>();

        for (String file : javaFiles) {
            Path source =
                    experimentRoot
                            .resolve(file)
                            .normalize();

            if (!source.startsWith(experimentRoot)) {
                return new JavaCompileResult(
                        false,
                        List.of(
                                "Unsafe Java source path: " + file
                        )
                );
            }

            if (!Files.isRegularFile(source)) {
                return new JavaCompileResult(
                        false,
                        List.of(
                                "Java source file does not exist: "
                                        + file
                        )
                );
            }

            sourcePaths.add(source);
        }

        if (sourcePaths.isEmpty()) {
            return new JavaCompileResult(
                    true,
                    List.of()
            );
        }

        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();

        try (
                StandardJavaFileManager fileManager =
                        compiler.getStandardFileManager(
                                diagnostics,
                                Locale.ROOT,
                                StandardCharsets.UTF_8
                        )
        ) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromFiles(
                            sourcePaths
                                    .stream()
                                    .map(Path::toFile)
                                    .toList()
                    );

            List<String> options = List.of(
                    "-proc:none",
                    "-encoding",
                    "UTF-8",
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

            List<String> errors =
                    new ArrayList<>();

            for (Diagnostic<? extends JavaFileObject> diagnostic :
                    diagnostics.getDiagnostics()) {

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

                errors.add(
                        "Java compile error in " +
                                sourceName +
                                " at line " +
                                diagnostic.getLineNumber() +
                                ": " +
                                diagnostic.getMessage(
                                        Locale.ROOT
                                )
                );
            }

            if (errors.isEmpty()) {
                errors.add(
                        "Java compilation failed."
                );
            }

            return new JavaCompileResult(
                    false,
                    List.copyOf(errors)
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
        if (content == null || content.isBlank()) {
            return false;
        }

        if (content.length() > 1_000_000) {
            return false;
        }

        String lower =
                content.toLowerCase(Locale.ROOT);

        if (lower.contains("todo: implement")
                || lower.contains("insert code here")
                || lower.contains("your code here")
                || lower.contains("implementation omitted")
                || lower.contains("code omitted")) {
            return false;
        }

        return isSupportedFile(file);
    }

    private boolean syntaxStructureTest(
            String file,
            String content
    ) {
        if (content == null) {
            return false;
        }

        if (isJava(file)
                || isJavaScript(file)
                || isCss(file)) {

            return balanced(
                    content,
                    '{',
                    '}'
            )
                    && balanced(
                    content,
                    '(',
                    ')'
            )
                    && balanced(
                    content,
                    '[',
                    ']'
            );
        }

        if (isHtml(file)) {
            return balancedHtml(content);
        }

        return true;
    }

    private boolean javaStructureTest(
            String content
    ) {
        if (content == null
                || content.isBlank()) {
            return false;
        }

        String trimmed =
                content.trim();

        boolean hasType =
                trimmed.contains("class ")
                        || trimmed.contains("record ")
                        || trimmed.contains("interface ")
                        || trimmed.contains("enum ");

        if (!hasType) {
            return false;
        }

        return !containsUnfinishedJava(
                content
        );
    }

    private boolean htmlStructureTest(
            String content
    ) {
        if (content == null
                || content.isBlank()) {
            return false;
        }

        String lower =
                content.toLowerCase(Locale.ROOT);

        if (!lower.contains("<html")
                && !lower.contains("<!doctype")) {
            return false;
        }

        if (!lower.contains("<body")
                && !lower.contains("<main")
                && !lower.contains("<div")) {
            return false;
        }

        return balancedHtml(content);
    }

    private boolean cssStructureTest(
            String content
    ) {
        if (content == null
                || content.isBlank()) {
            return false;
        }

        if (!balanced(
                content,
                '{',
                '}'
        )) {
            return false;
        }

        return !content
                .replaceAll("\\s+", "")
                .contains("{}");
    }

    private boolean javascriptStructureTest(
            String content
    ) {
        if (content == null
                || content.isBlank()) {
            return false;
        }

        if (!balanced(
                content,
                '{',
                '}'
        )
                || !balanced(
                content,
                '(',
                ')'
        )
                || !balanced(
                content,
                '[',
                ']'
        )) {
            return false;
        }

        String lower =
                content.toLowerCase(Locale.ROOT);

        return !lower.contains("debugger;")
                && !lower.contains(
                "javascript:javascript:"
        );
    }

    private boolean safetyContentTest(
            String content
    ) {
        if (content == null) {
            return false;
        }

        String lower =
                content.toLowerCase(Locale.ROOT);

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
                "credential theft"
        };

        for (String blockedValue : blocked) {
            if (lower.contains(blockedValue)) {
                return false;
            }
        }

        return true;
    }

    private boolean containsUnfinishedJava(
            String content
    ) {
        String lower =
                content.toLowerCase(Locale.ROOT);

        return lower.contains(
                "throw new unsupportedoperationexception"
        )
                || lower.contains(
                "return null; // todo"
        )
                || lower.contains(
                "todo implement"
        )
                || lower.contains(
                "implementation omitted"
        );
    }

    private boolean balanced(
            String content,
            char opening,
            char closing
    ) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if ((inString || inChar)
                    && c == '\\') {
                escaped = true;
                continue;
            }

            if (!inChar && c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString && c == '\'') {
                inChar = !inChar;
                continue;
            }

            if (inString || inChar) {
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
                && !inString
                && !inChar;
    }

    private boolean balancedHtml(
            String content
    ) {
        String lower =
                content.toLowerCase(Locale.ROOT);

        String[] tags = {
                "html",
                "head",
                "body",
                "main",
                "section",
                "div",
                "script",
                "style"
        };

        for (String tag : tags) {
            int open =
                    countOccurrences(
                            lower,
                            "<" + tag
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

        String normalized =
                file.replace(
                        '\\',
                        '/'
                );

        return normalized.startsWith(
                "src/main/"
        )
                || normalized.startsWith(
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
                && file.toLowerCase(
                Locale.ROOT
        ).endsWith(".java");
    }

    private boolean isHtml(
            String file
    ) {
        return file != null
                && file.toLowerCase(
                Locale.ROOT
        ).endsWith(".html");
    }

    private boolean isCss(
            String file
    ) {
        return file != null
                && file.toLowerCase(
                Locale.ROOT
        ).endsWith(".css");
    }

    private boolean isJavaScript(
            String file
    ) {
        return file != null
                && file.toLowerCase(
                Locale.ROOT
        ).endsWith(".js");
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
