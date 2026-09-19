package controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import service.AttachmentService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            AttachmentService.Attachment attachment = attachmentService.save(file);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", attachment.id(),
                    "name", attachment.originalName(),
                    "type", attachment.type(),
                    "size", attachment.size(),
                    "contentType", attachment.contentType(),
                    "url", "/api/attachments/" + attachment.id()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null ? "Upload failed" : e.getMessage()
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        try {
            AttachmentService.Attachment attachment = attachmentService.get(id);
            byte[] data = attachmentService.readBytes(id);

            String contentType = attachment.contentType();

            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            ContentDisposition disposition;

            if ("image".equalsIgnoreCase(attachment.type())) {
                disposition = ContentDisposition.inline()
                        .filename(attachment.originalName(), StandardCharsets.UTF_8)
                        .build();
            } else {
                disposition = ContentDisposition.attachment()
                        .filename(attachment.originalName(), StandardCharsets.UTF_8)
                        .build();
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(data.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(data);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<?> info(@PathVariable String id) {
        try {
            AttachmentService.AttachmentInfo info = attachmentService.get(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", info.id(),
                    "name", info.originalName(),
                    "type", info.type(),
                    "size", info.size(),
                    "contentType", info.contentType()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().body(Map.of(
                    "success", false,
                    "error", "Attachment not found"
            ));
        }
    }

    @GetMapping("/{id}/text")
    public ResponseEntity<?> text(@PathVariable String id) {
        try {
            if (!attachmentService.isText(id)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "This attachment is not a text file"
                ));
            }

            String content = attachmentService.readText(id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id,
                    "content", content
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null ? "Unable to read file" : e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            boolean deleted = attachmentService.delete(id);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() == null ? "Delete failed" : e.getMessage()
            ));
        }
    }
}
