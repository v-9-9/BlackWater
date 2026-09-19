package service;

import org.springframework.stereotype.Service;

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
                    0,
                    List.of("Experiment ID is missing.")
            );
        }

        if (!sandbox.exists(experimentId)) {
            return new TestResult(
                    experimentId,
                    false,
                    0,
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
                    0,
                    List.of("Verification failed: " + safeMessage(e))
            );
        }

        if (verification == null || !verification.safe()) {
            return new TestResult(
                    experimentId,
                    false,
                    0,
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
                    0,
                    List.of("Could not inspect experiment: " + safeMessage(e))
            );
        }

        if (files == null || files.isEmpty()) {
            return new TestResult(
                    experimentId,
                    false,
                    0,
                    List.of("Experiment contains no generated files.")
            );
        }

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String file = entry.getKey();
            String content = entry.getValue();

            if (!isProjectFile(file)) {
                continue;
            }

            if (content == null || content.isBlank()) {
                errors.add("Empty generated file: " + file);
                totalTests++;
                continue;
            }

            totalTests++;

            if (basicContentTest(file, content)) {
                passedTests++;
            } else {
                errors.add("Basic content test failed: " + file);
            }

            totalTests++;

            if (syntaxStructureTest(file, content)) {
                passedTests++;
            } else {
                errors.add("Structure test failed: " + file);
            }

            if (isJava(file)) {
                totalTests++;

                if (javaStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add("Java structure test failed: " + file);
                }
            }

            if (isHtml(file)) {
                totalTests++;

                if (htmlStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add("HTML structure test failed: " + file);
                }
            }

            if (isCss(file)) {
                totalTests++;

                if (cssStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add("CSS structure test failed: " + file);
                }
            }

            if (isJavaScript(file)) {
                totalTests++;

                if (javascriptStructureTest(content)) {
                    passedTests++;
                } else {
                    errors.add("JavaScript structure test failed: " + file);
                }
            }

            totalTests++;

            if (safetyContentTest(content)) {
                passedTests++;
            } else {
                errors.add("Safety content test failed: " + file);
            }
        }

        boolean success =
                totalTests > 0
                        && passedTests == totalTests
                        && errors.isEmpty();

        String summary =
                "Passed " + passedTests +
                        " / " + totalTests +
                        " tests.";

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
                totalTests == 0
                        ? 0.0
                        : (double) passedTests / totalTests,
                List.copyOf(errors)
        );
    }

    private boolean basicContentTest(
            String file,
            String content
    ) {
        String lower = content.toLowerCase(Locale.ROOT);

        if (lower.contains("todo: implement")
                || lower.contains("insert code here")
                || lower.contains("your code here")
                || lower.contains("implementation omitted")
                || lower.contains("code omitted")) {
            return false;
        }

        if (content.length() > 1_000_000) {
            return false;
        }

        return isSupportedFile(file);
    }

    private boolean syntaxStructureTest(
            String file,
            String content
    ) {
        if (isJava(file)
                || isJavaScript(file)
                || isCss(file)) {

            return balanced(content, '{', '}')
                    && balanced(content, '(', ')')
                    && balanced(content, '[', ']');
        }

        if (isHtml(file)) {
            return balancedHtml(content);
        }

        return true;
    }

    private boolean javaStructureTest(String content) {
        String trimmed = content.trim();

        if (!trimmed.contains("class ")
                && !trimmed.contains("record ")
                && !trimmed.contains("interface ")
                && !trimmed.contains("enum ")) {
            return false;
        }

        if (trimmed.contains("package ")
                && !trimmed.contains("import ")
                && !trimmed.contains("class ")
                && !trimmed.contains("record ")
                && !trimmed.contains("interface ")
                && !trimmed.contains("enum ")) {
            return false;
        }

        return !containsUnfinishedJava(content);
    }

    private boolean htmlStructureTest(String content) {
        String lower = content.toLowerCase(Locale.ROOT);

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

    private boolean cssStructureTest(String content) {
        if (!balanced(content, '{', '}')) {
            return false;
        }

        String trimmed = content.trim();

        if (trimmed.isEmpty()) {
            return false;
        }

        return !trimmed.contains("{ }");
    }

    private boolean javascriptStructureTest(String content) {
        if (!balanced(content, '{', '}')
                || !balanced(content, '(', ')')
                || !balanced(content, '[', ']')) {
            return false;
        }

        String lower = content.toLowerCase(Locale.ROOT);

        return !lower.contains("debugger;")
                && !lower.contains("javascript:javascript:");
    }

    private boolean safetyContentTest(String content) {
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

    private boolean containsUnfinishedJava(String content) {
        String lower =
                content.toLowerCase(Locale.ROOT);

        return lower.contains("throw new unsupportedoperationexception")
                || lower.contains("return null; // todo")
                || lower.contains("todo implement")
                || lower.contains("implementation omitted");
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

            if ((inString || inChar) && c == '\\') {
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

    private boolean balancedHtml(String content) {
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

        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }

        return count;
    }

    private boolean isProjectFile(String file) {
        if (file == null) {
            return false;
        }

        String normalized =
                file.replace('\\', '/');

        return normalized.startsWith("src/main/")
                || normalized.startsWith("src/test/");
    }

    private boolean isSupportedFile(String file) {
        String lower =
                file.toLowerCase(Locale.ROOT);

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

    private boolean isJava(String file) {
        return file.toLowerCase(Locale.ROOT)
                .endsWith(".java");
    }

    private boolean isHtml(String file) {
        return file.toLowerCase(Locale.ROOT)
                .endsWith(".html");
    }

    private boolean isCss(String file) {
        return file.toLowerCase(Locale.ROOT)
                .endsWith(".css");
    }

    private boolean isJavaScript(String file) {
        return file.toLowerCase(Locale.ROOT)
                .endsWith(".js");
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null) {
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
}
