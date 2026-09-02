package com.finotech.jewellery.modules.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Links an already-uploaded file to an item.
 *
 * <p>The binary goes to {@code POST /api/v1/files} first, which returns the
 * storage key. Keeping upload and linking separate means a large photo that
 * fails midway never leaves a half-written row against the item, and one upload
 * can be reused rather than re-sent.
 */
public record LinkImageRequest(@NotBlank @Size(max = 500) String storageKey,
                               @Size(max = 255) String fileName,
                               @Size(max = 100) String contentType,
                               Long sizeBytes,
                               boolean primaryImage,
                               Integer displayOrder) {
}
