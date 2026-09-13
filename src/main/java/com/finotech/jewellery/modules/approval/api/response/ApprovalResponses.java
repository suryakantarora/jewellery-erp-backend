package com.finotech.jewellery.modules.approval.api.response;

import com.finotech.jewellery.modules.approval.domain.entity.ApprovalDecisionRecord;
import com.finotech.jewellery.modules.approval.domain.entity.ApprovalInformationRequest;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalDecision;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Responses for the unified approvals endpoint. */
public final class ApprovalResponses {

    private ApprovalResponses() {
    }

    /** One row of the pending list, shaped the same whatever module it came from. */
    public record ApprovalItemResponse(ApprovalType type,
                                       UUID id,
                                       String reference,
                                       String summary,
                                       UUID branchId,
                                       String branchName,
                                       String requestedBy,
                                       Instant requestedAt,
                                       BigDecimal amount,
                                       String currency,
                                       boolean awaitingSecondApproval,
                                       boolean infoRequested) {

        public ApprovalItemResponse withFlags(String branchName, boolean infoRequested) {
            return new ApprovalItemResponse(type, id, reference, summary, branchId, branchName,
                    requestedBy, requestedAt, amount, currency, awaitingSecondApproval,
                    infoRequested);
        }
    }

    public record PendingCountResponse(long total, Map<ApprovalType, Long> byType) {
    }

    public record DecisionResponse(ApprovalType type, UUID id, ApprovalDecision decision,
                                   String status, String decidedBy, Instant decidedAt) {

        public static DecisionResponse from(ApprovalDecisionRecord d) {
            return new DecisionResponse(d.getApprovalType(), d.getReferenceId(), d.getDecision(),
                    d.getResultStatus(), d.getDecidedBy(), d.getCreatedAt());
        }
    }

    public record InformationRequestResponse(UUID id, ApprovalType type, UUID referenceId,
                                             String requestedBy, String message,
                                             Instant requestedAt, String answer,
                                             String answeredBy, Instant answeredAt,
                                             boolean open) {

        public static InformationRequestResponse from(ApprovalInformationRequest r) {
            return new InformationRequestResponse(r.getId(), r.getApprovalType(),
                    r.getReferenceId(), r.getRequestedBy(), r.getMessage(), r.getCreatedAt(),
                    r.getAnswer(), r.getAnsweredBy(), r.getAnsweredAt(), r.isOpen());
        }
    }
}
