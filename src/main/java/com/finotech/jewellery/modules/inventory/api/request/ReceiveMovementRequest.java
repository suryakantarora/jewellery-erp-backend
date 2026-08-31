package com.finotech.jewellery.modules.inventory.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Confirms physical receipt, optionally recording re-weighed values. */
public record ReceiveMovementRequest(List<ReceivedLine> lines,
                                     @Size(max = 500) String notes) {

    public record ReceivedLine(@NotNull UUID jewelleryItemId,
                               BigDecimal receivedWeight,
                               @Size(max = 500) String discrepancyNote) {
    }
}
