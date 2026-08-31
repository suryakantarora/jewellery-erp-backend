package com.finotech.jewellery.shared.storage;

import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Stores files on local disk.
 *
 * <p>The default, so uploads work out of the box in development. It is not
 * suitable for more than one application instance — several servers would each
 * hold different files — which is why a shared object store is the documented
 * production choice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jewellery.storage.provider", havingValue = "local",
        matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties properties;

    @Override
    public StoredFile store(String category, String originalFilename, String contentType,
                            long sizeBytes, InputStream content) {
        if (sizeBytes > properties.maxFileSizeBytes()) {
            throw new ValidationException("File exceeds the maximum of "
                    + (properties.maxFileSizeBytes() / (1024 * 1024)) + " MB");
        }
        if (!properties.isAllowed(contentType)) {
            throw new ValidationException("Files of type " + contentType + " are not accepted");
        }

        // The key is generated, never taken from the upload: a filename from a
        // client is untrusted input and must not be able to steer a path.
        String extension = extensionOf(originalFilename);
        String key = "%s/%s/%s%s".formatted(safeCategory(category),
                LocalDate.now(), UUID.randomUUID(), extension);

        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store file " + key, ex);
        }

        log.debug("Stored {} ({} bytes)", key, sizeBytes);
        return new StoredFile(key, originalFilename, contentType, sizeBytes);
    }

    @Override
    public InputStream retrieve(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) {
            throw new NotFoundException("No stored file with key " + storageKey);
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read file " + storageKey, ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not delete file " + storageKey, ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    /**
     * Resolves a key inside the storage root and refuses anything that escapes
     * it, which is what stops a crafted key reading arbitrary files.
     */
    private Path resolve(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new ValidationException("A storage key is required");
        }
        Path root = Path.of(properties.localPath()).toAbsolutePath().normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new ValidationException("Invalid storage key");
        }
        return resolved;
    }

    private String safeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "general";
        }
        return category.replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "");
        return extension.isEmpty() ? "" : "." + extension.toLowerCase();
    }
}
