package com.finotech.jewellery.modules.product.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Links an already-uploaded file to a product or design.
 *
 * <p>Same contract as the inventory item version: upload to
 * {@code POST /api/v1/files} first, then send the storage key. Duplicated
 * rather than shared because the product module must not depend on inventory.
 */
public record LinkImageRequest(@NotBlank @Size(max = 500) String storageKey,
                               @Size(max = 255) String fileName,
                               @Size(max = 100) String contentType,
                               Long sizeBytes,
                               boolean primaryImage,
                               Integer displayOrder) {
}
