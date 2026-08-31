package com.finotech.jewellery.modules.repair.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Requests for each step of the repair workflow. */
public final class RepairRequests {

    private RepairRequests() {
    }

    public record ReceiveRequest(@NotNull UUID customerId,
                                 @NotNull UUID branchId,
                                 UUID jewelleryItemId,
                                 @NotBlank @Size(max = 500) String itemDescription,
                                 @NotBlank @Size(max = 1000) String reportedProblem,
                                 @Size(max = 1000) String conditionOnArrival,
                                 @DecimalMin("0.0") BigDecimal receivedWeight,
                                 @Size(max = 1000) String conditionPhotoKeys,
                                 LocalDate promisedDate,
                                 @Size(max = 500) String notes) {
    }

    public record InspectionRequest(@NotBlank @Size(max = 1000) String findings,
                                    @Size(max = 1000) String conditionNotes) {
    }

    public record EstimateRequest(@NotNull @DecimalMin("0.0") BigDecimal estimatedCost,
                                  @Positive Integer estimatedDays,
                                  @Size(max = 1000) String estimateNotes) {
    }

    public record CustomerDecisionRequest(boolean approved,
                                          @Size(max = 500) String declineReason) {
    }

    public record AssignRequest(@NotBlank @Size(max = 100) String assignedTo,
                                @Size(max = 500) String notes) {
    }

    public record CompleteWorkRequest(@DecimalMin("0.0") BigDecimal finalCost,
                                      @Size(max = 1000) String notes) {
    }

    public record QualityCheckRequest(boolean passed,
                                      @Size(max = 1000) String notes) {
    }

    public record DeliverRequest(@NotBlank @Size(max = 150) String deliveredTo,
                                 @DecimalMin("0.0") BigDecimal deliveredWeight,
                                 UUID returnToLocationId,
                                 @Size(max = 500) String notes) {
    }
}
