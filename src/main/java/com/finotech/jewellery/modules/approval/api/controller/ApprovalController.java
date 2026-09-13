package com.finotech.jewellery.modules.approval.api.controller;

import com.finotech.jewellery.modules.approval.api.request.ApprovalRequests.AnswerRequest;
import com.finotech.jewellery.modules.approval.api.request.ApprovalRequests.DecisionRequest;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.ApprovalItemResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.DecisionResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.InformationRequestResponse;
import com.finotech.jewellery.modules.approval.api.response.ApprovalResponses.PendingCountResponse;
import com.finotech.jewellery.modules.approval.application.service.ApprovalService;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One queue for everything awaiting sign-off. The controller only checks that
 * the caller can approve <em>something</em>; which types they see, and whether
 * they may decide a given record, is settled in the service where the type is
 * known.
 */
@Tag(name = "Approvals")
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private static final String ANY_APPROVER = "hasAnyAuthority("
            + "'INVENTORY_TRANSFER_APPROVE', 'PROCUREMENT_APPROVE', 'EXCHANGE_APPROVE', "
            + "'STOCK_COUNT_APPROVE', 'PROCUREMENT_RECEIVE', 'DISCOUNT_APPROVE')";
    private static final String ANY_PARTICIPANT = "hasAnyAuthority("
            + "'INVENTORY_TRANSFER_APPROVE', 'PROCUREMENT_APPROVE', 'EXCHANGE_APPROVE', "
            + "'STOCK_COUNT_APPROVE', 'PROCUREMENT_RECEIVE', 'DISCOUNT_APPROVE', "
            + "'INVENTORY_TRANSFER', 'PROCUREMENT_CREATE', 'EXCHANGE_PROCESS', "
            + "'STOCK_COUNT_PERFORM', 'DISCOUNT_REQUEST')";

    private final ApprovalService approvalService;

    @Operation(summary = "Everything the caller can approve, oldest first",
            description = "Only types whose approve permission the caller holds, and only "
                    + "records in branches the caller can access. Capped at 200 rows.")
    @GetMapping("/pending")
    @PreAuthorize(ANY_APPROVER)
    public ResponseEntity<ApiResponse<List<ApprovalItemResponse>>> pending(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) ApprovalType type) {
        return ResponseEntity.ok(ApiResponse.ok(approvalService.pending(branchId, type)));
    }

    @Operation(summary = "Count of pending approvals, for the dashboard badge")
    @GetMapping("/pending/count")
    @PreAuthorize(ANY_APPROVER)
    public ResponseEntity<ApiResponse<PendingCountResponse>> pendingCount(
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(approvalService.pendingCount(branchId)));
    }

    @Operation(summary = "Approve, reject, or ask for more information",
            description = "Dispatches to the owning module. REQUEST_INFO records a question "
                    + "without changing the record's status. Send X-Idempotency-Key so a "
                    + "retried decision returns the original outcome; the same key with a "
                    + "different body is rejected with IDEMPOTENCY_CONFLICT.")
    @PostMapping("/{type}/{id}/decision")
    @PreAuthorize(ANY_APPROVER)
    public ResponseEntity<ApiResponse<DecisionResponse>> decide(
            @PathVariable ApprovalType type,
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.ok(
                approvalService.decide(type, id, request, idempotencyKey)));
    }

    @Operation(summary = "Questions asked on a record, and their answers")
    @GetMapping("/{type}/{id}/information")
    @PreAuthorize(ANY_PARTICIPANT)
    public ResponseEntity<ApiResponse<List<InformationRequestResponse>>> information(
            @PathVariable ApprovalType type, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(approvalService.information(type, id)));
    }

    @Operation(summary = "Answer an approver's question",
            description = "Open to anyone holding the permission that raises this type of record.")
    @PostMapping("/{type}/{id}/information/{requestId}/answer")
    @PreAuthorize(ANY_PARTICIPANT)
    public ResponseEntity<ApiResponse<InformationRequestResponse>> answer(
            @PathVariable ApprovalType type,
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                approvalService.answer(type, id, requestId, request.answer())));
    }
}
