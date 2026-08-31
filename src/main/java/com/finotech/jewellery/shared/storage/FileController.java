package com.finotech.jewellery.shared.storage;

import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload and download for the documents and images the business modules
 * reference by key: certificates, customer documents, product photographs and
 * repair condition shots.
 *
 * <p>Upload returns a storage key. The caller then puts that key on whichever
 * record it belongs to — the file and the record it describes are deliberately
 * separate, because a certificate can be uploaded before anyone knows which item
 * it will be attached to.
 */
@Tag(name = "Files")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storage;
    private final AuditService auditService;

    @Operation(summary = "Upload a file",
            description = "Returns a storage key to record against a customer document, "
                    + "certificate, product image or repair photograph. Type and size are "
                    + "checked against the configured allow-list.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FILE_UPLOAD')")
    public ResponseEntity<ApiResponse<FileStorageService.StoredFile>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String category) {
        if (file.isEmpty()) {
            throw new ValidationException("The uploaded file is empty");
        }
        try {
            FileStorageService.StoredFile stored = storage.store(category,
                    file.getOriginalFilename(), file.getContentType(), file.getSize(),
                    file.getInputStream());

            auditService.record("FILE_UPLOADED", "File", stored.storageKey(), null,
                    Map.of("category", category, "sizeBytes", stored.sizeBytes(),
                            "contentType", String.valueOf(stored.contentType())));

            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(stored));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
    }

    @Operation(summary = "Download a stored file")
    @GetMapping
    @PreAuthorize("hasAuthority('FILE_DOWNLOAD')")
    public ResponseEntity<InputStreamResource> download(@RequestParam String key) {
        InputStreamResource resource = new InputStreamResource(storage.retrieve(key));
        // Served as an attachment: content came from a user upload and must never
        // be rendered inline in a browser.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(resource);
    }
}
