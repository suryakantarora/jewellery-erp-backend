package com.finotech.jewellery.modules.product.api.response;

import com.finotech.jewellery.modules.product.domain.entity.DesignImage;
import com.finotech.jewellery.modules.product.domain.entity.ProductImage;
import java.util.UUID;

/**
 * A linked catalogue image. The storage key is returned rather than a URL: the
 * client fetches bytes through {@code GET /api/v1/files?key=...}, which applies
 * the same download permission as every other file.
 */
public record ImageResponse(UUID id, String storageKey, String fileName, String contentType,
                            Long sizeBytes, boolean primaryImage, Integer displayOrder) {

    public static ImageResponse from(ProductImage image) {
        return new ImageResponse(image.getId(), image.getStorageKey(), image.getFileName(),
                image.getContentType(), image.getSizeBytes(), image.isPrimaryImage(),
                image.getDisplayOrder());
    }

    public static ImageResponse from(DesignImage image) {
        return new ImageResponse(image.getId(), image.getStorageKey(), image.getFileName(),
                image.getContentType(), image.getSizeBytes(), image.isPrimaryImage(),
                image.getDisplayOrder());
    }
}
