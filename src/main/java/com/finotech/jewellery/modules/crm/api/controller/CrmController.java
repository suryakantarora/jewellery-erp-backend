package com.finotech.jewellery.modules.crm.api.controller;

import com.finotech.jewellery.modules.crm.api.request.CrmRequests;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.ActivityResponse;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.CampaignResponse;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.Customer360Response;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.FollowUpResponse;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.SegmentResponse;
import com.finotech.jewellery.modules.crm.application.service.CampaignService;
import com.finotech.jewellery.modules.crm.application.service.CrmService;
import com.finotech.jewellery.modules.crm.application.service.SegmentService;
import com.finotech.jewellery.modules.crm.domain.enums.ActivityType;
import com.finotech.jewellery.modules.crm.domain.enums.CampaignStatus;
import com.finotech.jewellery.modules.crm.domain.enums.FollowUpStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CRM")
@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
public class CrmController {

    private static final String VIEW = "hasAuthority('CRM_VIEW')";
    private static final String MANAGE = "hasAuthority('CRM_MANAGE')";
    private static final String CAMPAIGN = "hasAuthority('CAMPAIGN_MANAGE')";

    private final CrmService crmService;
    private final SegmentService segmentService;
    private final CampaignService campaignService;

    @Operation(summary = "Customer 360",
            description = "Identity, purchase history, loyalty standing, recent contact and "
                    + "open follow-ups, assembled from each owning module.")
    @GetMapping("/customers/{customerId}/360")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<Customer360Response>> customer360(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(crmService.customer360(customerId)));
    }

    // ---------- activities ----------

    @Operation(summary = "Search customer activities")
    @GetMapping("/activities")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<ActivityResponse>>> searchActivities(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) ActivityType activityType,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                crmService.searchActivities(customerId, activityType, branchId, pageable)));
    }

    @Operation(summary = "Log an interaction with a customer")
    @PostMapping("/activities")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ActivityResponse>> logActivity(
            @Valid @RequestBody CrmRequests.ActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(crmService.logActivity(request)));
    }

    // ---------- follow-ups ----------

    @Operation(summary = "Search follow-ups")
    @GetMapping("/follow-ups")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<FollowUpResponse>>> searchFollowUps(
            @RequestParam(required = false) FollowUpStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dueBefore,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(crmService.searchFollowUps(status, customerId,
                assignedTo, branchId, dueBefore, pageable)));
    }

    @Operation(summary = "My open follow-ups, soonest first")
    @GetMapping("/follow-ups/mine")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<FollowUpResponse>>> myFollowUps(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(crmService.myFollowUps(pageable)));
    }

    @Operation(summary = "Create a follow-up task")
    @PostMapping("/follow-ups")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FollowUpResponse>> createFollowUp(
            @Valid @RequestBody CrmRequests.FollowUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(crmService.createFollowUp(request)));
    }

    @Operation(summary = "Complete a follow-up and record the outcome")
    @PostMapping("/follow-ups/{id}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FollowUpResponse>> completeFollowUp(
            @PathVariable UUID id,
            @Valid @RequestBody CrmRequests.CompleteFollowUpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(crmService.completeFollowUp(id, request)));
    }

    @Operation(summary = "Cancel a follow-up")
    @PostMapping("/follow-ups/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<FollowUpResponse>> cancelFollowUp(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(crmService.cancelFollowUp(id, reason)));
    }

    // ---------- segments ----------

    @Operation(summary = "List customer segments")
    @GetMapping("/segments")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<SegmentResponse>>> listSegments() {
        return ResponseEntity.ok(ApiResponse.ok(segmentService.list()));
    }

    @Operation(summary = "Create a segment")
    @PostMapping("/segments")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SegmentResponse>> createSegment(
            @Valid @RequestBody CrmRequests.SegmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(segmentService.create(request)));
    }

    @Operation(summary = "Update a segment")
    @PutMapping("/segments/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<SegmentResponse>> updateSegment(
            @PathVariable UUID id, @Valid @RequestBody CrmRequests.SegmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(segmentService.update(id, request)));
    }

    @Operation(summary = "Resolve who currently qualifies for a segment")
    @GetMapping("/segments/{id}/members")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<UUID>>> evaluateSegment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(segmentService.evaluate(id)));
    }

    // ---------- campaigns ----------

    @Operation(summary = "Search campaigns")
    @GetMapping("/campaigns")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<CampaignResponse>>> searchCampaigns(
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                campaignService.search(status, branchId, pageable)));
    }

    @Operation(summary = "Get a campaign")
    @GetMapping("/campaigns/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<CampaignResponse>> getCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.get(id)));
    }

    @Operation(summary = "Create a campaign")
    @PostMapping("/campaigns")
    @PreAuthorize(CAMPAIGN)
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(
            @Valid @RequestBody CrmRequests.CampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(campaignService.create(request)));
    }

    @Operation(summary = "Preview who a campaign would reach, without sending")
    @GetMapping("/campaigns/{id}/preview")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<UUID>>> previewCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.preview(id)));
    }

    @Operation(summary = "Launch a campaign",
            description = "Resolves the segment and queues a message per recipient. "
                    + "A campaign can only be launched once.")
    @PostMapping("/campaigns/{id}/launch")
    @PreAuthorize(CAMPAIGN)
    public ResponseEntity<ApiResponse<CampaignResponse>> launchCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.launch(id)));
    }

    @Operation(summary = "Mark a campaign complete")
    @PostMapping("/campaigns/{id}/complete")
    @PreAuthorize(CAMPAIGN)
    public ResponseEntity<ApiResponse<CampaignResponse>> completeCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.complete(id)));
    }

    @Operation(summary = "Cancel a campaign")
    @PostMapping("/campaigns/{id}/cancel")
    @PreAuthorize(CAMPAIGN)
    public ResponseEntity<ApiResponse<CampaignResponse>> cancelCampaign(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(campaignService.cancel(id, reason)));
    }
}
