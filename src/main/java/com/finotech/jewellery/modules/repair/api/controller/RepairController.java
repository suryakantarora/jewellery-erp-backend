package com.finotech.jewellery.modules.repair.api.controller;

import com.finotech.jewellery.modules.repair.api.request.RepairRequests;
import com.finotech.jewellery.modules.repair.api.response.RepairResponse;
import com.finotech.jewellery.modules.repair.application.service.RepairService;
import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

@Tag(name = "Repairs")
@RestController
@RequestMapping("/api/v1/repairs")
@RequiredArgsConstructor
public class RepairController {

    private static final String VIEW = "hasAuthority('REPAIR_VIEW')";
    private static final String PROCESS = "hasAuthority('REPAIR_PROCESS')";
    private static final String ESTIMATE = "hasAuthority('REPAIR_ESTIMATE')";

    private final RepairService repairService;

    @Operation(summary = "Search repair jobs")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<RepairResponse>>> search(
            @RequestParam(required = false) RepairStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String assignedTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                repairService.search(status, customerId, branchId, assignedTo, pageable)));
    }

    @Operation(summary = "Get a repair with its full status history")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<RepairResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.get(id)));
    }

    @Operation(summary = "Jobs past their promised date and not yet ready")
    @GetMapping("/overdue")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<RepairResponse>>> overdue(@RequestParam UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.overdue(branchId)));
    }

    @Operation(summary = "Step 1 — take in a piece for repair and record its condition",
            description = "Send X-Idempotency-Key to make retries safe.")
    @PostMapping
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> receive(
            @Valid @RequestBody RepairRequests.ReceiveRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(repairService.receive(request, idempotencyKey)));
    }

    @Operation(summary = "Step 2 — record inspection findings")
    @PostMapping("/{id}/inspection")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> inspect(
            @PathVariable UUID id, @Valid @RequestBody RepairRequests.InspectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.inspect(id, request)));
    }

    @Operation(summary = "Step 3 — give an estimate and put it to the customer")
    @PostMapping("/{id}/estimate")
    @PreAuthorize(ESTIMATE)
    public ResponseEntity<ApiResponse<RepairResponse>> estimate(
            @PathVariable UUID id, @Valid @RequestBody RepairRequests.EstimateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.estimate(id, request)));
    }

    @Operation(summary = "Step 4 — record the customer's decision",
            description = "Work cannot begin until the estimate is accepted.")
    @PostMapping("/{id}/customer-decision")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> customerDecision(
            @PathVariable UUID id,
            @Valid @RequestBody RepairRequests.CustomerDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                repairService.recordCustomerDecision(id, request)));
    }

    @Operation(summary = "Assign the job to a craftsperson")
    @PostMapping("/{id}/assign")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> assign(
            @PathVariable UUID id, @Valid @RequestBody RepairRequests.AssignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.assign(id, request)));
    }

    @Operation(summary = "Step 5 — work finished, send for quality check")
    @PostMapping("/{id}/complete-work")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> completeWork(
            @PathVariable UUID id, @Valid @RequestBody RepairRequests.CompleteWorkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.completeWork(id, request)));
    }

    @Operation(summary = "Step 6 — quality check; failing returns the job to the bench")
    @PostMapping("/{id}/quality-check")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> qualityCheck(
            @PathVariable UUID id, @Valid @RequestBody RepairRequests.QualityCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.qualityCheck(id, request)));
    }

    @Operation(summary = "Step 7 — hand the piece back to the customer")
    @PostMapping("/{id}/deliver")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> deliver(
            @PathVariable UUID id, @Valid @RequestBody RepairRequests.DeliverRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.deliver(id, request)));
    }

    @Operation(summary = "Cancel a repair")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(PROCESS)
    public ResponseEntity<ApiResponse<RepairResponse>> cancel(
            @PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.ok(repairService.cancel(id, reason)));
    }
}
