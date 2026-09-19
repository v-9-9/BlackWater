package service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class AttachmentService {

    private static final Path STORAGE =
            Path.of("blackwater-files")
                    .toAbsolutePath()
                    .normalize();

    private static final long MAX_FILE_SIZE =
            25L * 1024L * 1024L;

    private static final long MAX_TEXT_SIZE =
            2L * 1024L * 1024L;

    private static final List<String> ALLOWED_EXTENSIONS =
            List.of(
                    "txt",
                    "md",
                    "json",
                    "xml",
                    "csv",
                    "java",
                    "html",
                    "css",
                    "js",
                    "ts",
                    "jsx",
                    "tsx",
                    "properties",
                    "yml",
                    "yaml",
                    "log",
                    "sql",
                    "py",
                    "c",
                    "cpp",
                    "h",
                    "hpp",
                    "sh",
                    "bat",
                    "pdf",
                    "doc",
                    "docx",
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "gif",
                    "bmp",
                    "svg"
            );

    public AttachmentService() {

        try {
            Files.createDirectories(STORAGE);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create attachment storage.",
                    exception
            );
        }
    }

    public Attachment save(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "File is empty."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File exceeds the 25 MB limit."
            );
        }

        String originalName =
                sanitizeFileName(
                        file.getOriginalFilename()
                );

        String extension =
                getExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "File type is not supported: "
                            + extension
            );
        }

        String id =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        String storedName =
                id
                        + (extension.isBlank()
                        ? ""
                        : "." + extension);

        Path target =
                STORAGE
                        .resolve(storedName)
                        .normalize();

        if (!target.startsWith(STORAGE)) {
            throw new IllegalArgumentException(
                    "Invalid file path."
            );
        }

        try {

            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return new Attachment(
                    id,
                    originalName,
                    storedName,
                    extension,
                    detectType(extension),
                    file.getSize(),
                    file.getContentType(),
                    Instant.now()
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not store attachment.",
                    exception
            );
        }
    }

    public AttachmentInfo get(String id) {

        String safeId =
                sanitizeId(id);

        Path file =
                findStoredFile(safeId);

        if (file == null) {
            return null;
        }

        try {

            String storedName =
                    file.getFileName()
                            .toString();

            String extension =
                    getExtension(storedName);

            return new AttachmentInfo(
                    safeId,
                    storedName,
                    extension,
                    detectType(extension),
                    Files.size(file)
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not read attachment.",
                    exception
            );
        }
    }

    public byte[] readBytes(String id) {

        Path file =
                requireFile(id);

        try {

            return Files.readAllBytes(file);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not read attachment.",
                    exception
            );
        }
    }

    public String readText(String id) {

        Path file =
                requireFile(id);

        try {

            long size =
                    Files.size(file);

            if (size > MAX_TEXT_SIZE) {
                throw new IllegalArgumentException(
                        "Text file exceeds the 2 MB processing limit."
                );
            }

            return Files.readString(
                    file,
                    StandardCharsets.UTF_8
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not read text attachment.",
                    exception
            );
        }
    }

    public String readBase64(String id) {

        return Base64.getEncoder()
                .encodeToString(
                        readBytes(id)
                );
    }

    public boolean isText(String id) {

        AttachmentInfo info =
                get(id);

        return info != null
                && "text".equals(info.type());
    }

    public boolean isImage(String id) {

        AttachmentInfo info =
                get(id);

        return info != null
                && "image".equals(info.type());
    }

    public boolean exists(String id) {

        return findStoredFile(
                sanitizeId(id)
        ) != null;
    }

    public void delete(String id) {

        Path file =
                findStoredFile(
                        sanitizeId(id)
                );

        if (file == null) {
            return;
        }

        try {

            Files.deleteIfExists(file);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not delete attachment.",
                    exception
            );
        }
    }

    public int cleanup() {

        int deleted = 0;

        try (Stream<Path> files =
                     Files.list(STORAGE)) {

            List<Path> paths =
                    files
                            .filter(Files::isRegularFile)
                            .sorted(
                                    Comparator.comparing(
                                            path -> path
                                                    .getFileName()
                                                    .toString()
                                    )
                            )
                            .toList();

            for (Path file : paths) {

                try {

                    Files.deleteIfExists(file);

                    deleted++;

                } catch (IOException ignored) {
                    // Ignore individual cleanup failures.
                }
            }

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not clean attachment storage.",
                    exception
            );
        }

        return deleted;
    }

    public Path getStoragePath() {
        return STORAGE;
    }

    private Path requireFile(String id) {

        Path file =
                findStoredFile(
                        sanitizeId(id)
                );

        if (file == null) {
            throw new IllegalArgumentException(
                    "Attachment not found."
            );
        }

        return file;
    }

    private Path findStoredFile(String id) {

        if (id == null
                || id.isBlank()
                || !id.matches("[a-zA-Z0-9_-]{16,64}")) {
            return null;
        }

        try (Stream<Path> files =
                     Files.list(STORAGE)) {

            return files
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .startsWith(id)
                    )
                    .findFirst()
                    .orElse(null);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not inspect attachment storage.",
                    exception
            );
        }
    }

    private String sanitizeId(String id) {

        if (id == null) {
            return "";
        }

        return id.trim();
    }

    private String sanitizeFileName(String name) {

        if (name == null || name.isBlank()) {
            return "unnamed-file";
        }

        String cleaned =
                name.replace("\\", "/");

        int slash =
                cleaned.lastIndexOf('/');

        if (slash >= 0) {
            cleaned =
                    cleaned.substring(
                            slash + 1
                    );
        }

        cleaned =
                cleaned.replaceAll(
                        "[^a-zA-Z0-9._()\\- ]",
                        "_"
                );

        if (cleaned.length() > 180) {
            cleaned =
                    cleaned.substring(
                            cleaned.length() - 180
                    );
        }

        return cleaned.isBlank()
                ? "unnamed-file"
                : cleaned;
    }

    private String getExtension(String fileName) {

        if (fileName == null) {
            return "";
        }

        int dot =
                fileName.lastIndexOf('.');

        if (
                dot < 0
                        || dot == fileName.length() - 1
        ) {
            return "";
        }

        return fileName
                .substring(dot + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String detectType(String extension) {

        if (
                List.of(
                        "png",
                        "jpg",
                        "jpeg",
                        "webp",
                        "gif",
                        "bmp",
                        "svg"
                ).contains(extension)
        ) {
            return "image";
        }

        if (
                List.of(
                        "pdf",
                        "doc",
                        "docx"
                ).contains(extension)
        ) {
            return "document";
        }

        return "text";
    }

    public record Attachment(
            String id,
            String originalName,
            String storedName,
            String extension,
            String type,
            long size,
            String contentType,
            Instant uploadedAt
    ) {
    }

    public record AttachmentInfo(
            String id,
            String storedName,
            String extension,
            String type,
            long size
    ) {
    }
}
