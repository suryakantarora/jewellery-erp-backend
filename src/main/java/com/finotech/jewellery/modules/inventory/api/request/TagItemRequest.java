package com.finotech.jewellery.modules.inventory.api.request;

import jakarta.validation.constraints.Size;

/** Attaches the physical identifiers that make up the digital passport. */
public record TagItemRequest(@Size(max = 100) String rfidTag,
                             @Size(max = 100) String qrCode,
                             @Size(max = 100) String barcode) {
}
