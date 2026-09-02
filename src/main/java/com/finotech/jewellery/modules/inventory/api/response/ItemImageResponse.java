package com.finotech.jewellery.modules.inventory.api.response;

import com.finotech.jewellery.modules.inventory.domain.entity.ItemImage;
import java.util.UUID;

/**
 * The storage key is returned rather than a URL: the client fetches bytes
 * through {@code GET /api/v1/files?key=...}, which applies the same download
 * permission as every other file.
 */
public record ItemImageResponse(UUID id, String storageKey, String fileName, String contentType,
                                Long sizeBytes, boolean primaryImage, Integer displayOrder) {

    public static ItemImageResponse from(ItemImage image) {
        return new ItemImageResponse(image.getId(), image.getStorageKey(), image.getFileName(),
                image.getContentType(), image.getSizeBytes(), image.isPrimaryImage(),
                image.getDisplayOrder());
    }
}
