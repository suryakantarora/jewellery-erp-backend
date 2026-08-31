package com.finotech.jewellery.shared.storage;

import java.io.InputStream;

/**
 * Object storage for documents and images (section 10).
 *
 * <p>Files never go in PostgreSQL; the database holds only the returned key.
 * Implementations are interchangeable, so a deployment can move from local disk
 * to S3 or MinIO without touching a business module.
 */
public interface FileStorageService {

    /**
     * Stores a file and returns the key to keep in the database.
     *
     * @param category logical folder, e.g. {@code customer-documents}
     * @param originalFilename used only to derive an extension; never trusted as
     *                         a path
     */
    StoredFile store(String category, String originalFilename, String contentType,
                     long sizeBytes, InputStream content);

    /** @throws com.finotech.jewellery.shared.exception.NotFoundException if absent */
    InputStream retrieve(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);

    record StoredFile(String storageKey, String fileName, String contentType, long sizeBytes) {
    }
}
