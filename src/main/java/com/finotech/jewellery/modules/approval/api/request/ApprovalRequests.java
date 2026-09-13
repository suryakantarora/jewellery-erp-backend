package com.finotech.jewellery.modules.approval.api.request;

import com.finotech.jewellery.modules.approval.domain.enums.ApprovalDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Requests for the unified approvals endpoint. */
public final class ApprovalRequests {

    private ApprovalRequests() {
    }

    /**
     * @param reason required for REJECT and REQUEST_INFO; an optional note for
     *               APPROVE
     */
    public record DecisionRequest(@NotNull ApprovalDecision decision,
                                  @Size(max = 500) String reason) {
    }

    public record AnswerRequest(@NotBlank @Size(max = 500) String answer) {
    }
}
