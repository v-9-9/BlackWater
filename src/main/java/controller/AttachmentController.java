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

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            AttachmentService.Attachment attachment =
                    attachmentService.save(file);

            return ResponseEntity.ok(attachment);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            e.getMessage() == null
                                    ? "Invalid file."
                                    : e.getMessage()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of(
                            "error",
                            "Could not save attachment."
                    )
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> download(
            @PathVariable String id
    ) {
        try {
            AttachmentService.AttachmentInfo info =
                    attachmentService.get(id);

            byte[] data =
                    attachmentService.readBytes(id);

            String contentType =
                    info.contentType() == null
                            || info.contentType().isBlank()
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : info.contentType();

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.parseMediaType(contentType)
            );

            headers.setContentLength(data.length);

            ContentDisposition disposition =
                    info.type().equals("image")
                            ? ContentDisposition.inline()
                            .filename(
                                    info.originalName(),
                                    StandardCharsets.UTF_8
                            )
                            .build()
                            : ContentDisposition.attachment()
                            .filename(
                                    info.originalName(),
                                    StandardCharsets.UTF_8
                            )
                            .build();

            headers.setContentDisposition(
                    disposition
            );

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(data);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage() == null
                                            ? "Invalid attachment."
                                            : e.getMessage()
                            )
                    );

        } catch (Exception e) {
            return ResponseEntity
                    .notFound()
                    .body(
                            Map.of(
                                    "error",
                                    "Attachment not found."
                            )
                    );
        }
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<?> info(
            @PathVariable String id
    ) {
        try {
            return ResponseEntity.ok(
                    attachmentService.get(id)
            );

        } catch (Exception e) {
            return ResponseEntity
                    .notFound()
                    .body(
                            Map.of(
                                    "error",
                                    "Attachment not found."
                            )
                    );
        }
    }

    @GetMapping(
            value = "/{id}/text",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<?> text(
            @PathVariable String id
    ) {
        try {
            if (!attachmentService.isText(id)) {
                return ResponseEntity
                        .badRequest()
                        .body("Attachment is not a text file.");
            }

            return ResponseEntity.ok(
                    attachmentService.readText(id)
            );

        } catch (Exception e) {
            return ResponseEntity
                    .notFound()
                    .body("Attachment not found.");
        }
    }

    @GetMapping(
            value = "/{id}/base64",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<?> base64(
            @PathVariable String id
    ) {
        try {
            if (!attachmentService.isImage(id)) {
                return ResponseEntity
                        .badRequest()
                        .body("Attachment is not an image.");
            }

            return ResponseEntity.ok(
                    attachmentService.readBase64(id)
            );

        } catch (Exception e) {
            return ResponseEntity
                    .notFound()
                    .body("Attachment not found.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id
    ) {
        try {
            boolean deleted =
                    attachmentService.delete(id);

            if (!deleted) {
                return ResponseEntity
                        .notFound()
                        .body(
                                Map.of(
                                        "error",
                                        "Attachment not found."
                                )
                        );
            }

            return ResponseEntity.ok(
                    Map.of(
                            "deleted",
                            true,
                            "id",
                            id
                    )
            );

        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "error",
                                    "Could not delete attachment."
                            )
                    );
        }
    }
}
