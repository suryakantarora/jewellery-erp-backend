package com.finotech.jewellery.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.storage.FileStorageService;
import com.finotech.jewellery.shared.storage.LocalFileStorageService;
import com.finotech.jewellery.shared.storage.StorageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A filename from a client is untrusted input, so what the storage layer refuses
 * matters as much as what it stores.
 */
class FileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("a permitted file is stored and read back unchanged")
    void storeAndRetrieve() throws IOException {
        FileStorageService storage = storage();
        byte[] content = "certificate contents".getBytes(StandardCharsets.UTF_8);

        var stored = storage.store("certificates", "gia-report.pdf", "application/pdf",
                content.length, new ByteArrayInputStream(content));

        assertThat(stored.storageKey()).startsWith("certificates/").endsWith(".pdf");
        assertThat(storage.exists(stored.storageKey())).isTrue();
        try (var in = storage.retrieve(stored.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("the stored key is generated, never taken from the upload")
    void keyIsNotDerivedFromTheClientFilename() {
        FileStorageService storage = storage();
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);

        var stored = storage.store("certificates", "../../../etc/passwd.pdf", "application/pdf",
                content.length, new ByteArrayInputStream(content));

        // Only the extension is taken from the client; the path is ours.
        assertThat(stored.storageKey()).doesNotContain("..").doesNotContain("passwd");
        assertThat(stored.storageKey()).startsWith("certificates/");
    }

    @Test
    @DisplayName("a key that escapes the storage root is refused")
    void pathTraversalIsRefused() {
        FileStorageService storage = storage();
        assertThatThrownBy(() -> storage.retrieve("../../../../etc/passwd"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid storage key");
    }

    @Test
    @DisplayName("a disallowed content type is refused")
    void disallowedTypeIsRefused() {
        FileStorageService storage = storage();
        byte[] content = "#!/bin/sh".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.store("general", "run.sh", "application/x-sh",
                content.length, new ByteArrayInputStream(content)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not accepted");
    }

    @Test
    @DisplayName("an oversized file is refused before anything is written")
    void oversizedFileIsRefused() {
        FileStorageService storage = storage();
        byte[] content = new byte[10];

        assertThatThrownBy(() -> storage.store("general", "big.pdf", "application/pdf",
                999_999_999L, new ByteArrayInputStream(content)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    @DisplayName("retrieving a key that does not exist reports not found")
    void missingKeyIsReported() {
        FileStorageService storage = storage();
        assertThatThrownBy(() -> storage.retrieve("certificates/2026-01-01/nothing.pdf"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleting is safe to repeat")
    void deleteIsIdempotent() {
        FileStorageService storage = storage();
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        var stored = storage.store("general", "a.png", "image/png", content.length,
                new ByteArrayInputStream(content));

        storage.delete(stored.storageKey());
        storage.delete(stored.storageKey());

        assertThat(storage.exists(stored.storageKey())).isFalse();
    }

    private FileStorageService storage() {
        return new LocalFileStorageService(new StorageProperties(null, "jewellery",
                tempDir.toString(), 1024 * 1024,
                List.of("image/jpeg", "image/png", "image/webp", "application/pdf")));
    }
}
