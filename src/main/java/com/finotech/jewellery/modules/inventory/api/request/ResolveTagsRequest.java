package com.finotech.jewellery.modules.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A batch of scanned tags — RFID, QR, barcode or item code — to resolve at
 * once. An RFID gate reads a whole tray in one sweep; resolving each tag with
 * its own request would make the stock-check screen as slow as counting by
 * hand.
 */
public record ResolveTagsRequest(@NotEmpty @Size(max = 500) List<@NotBlank @Size(max = 100) String> tags) {
}
