package com.finotech.jewellery.modules.crm.application.service;

import com.finotech.jewellery.modules.crm.api.request.CrmRequests;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.CampaignResponse;
import com.finotech.jewellery.modules.crm.domain.entity.Campaign;
import com.finotech.jewellery.modules.crm.domain.enums.CampaignStatus;
import com.finotech.jewellery.modules.crm.infrastructure.repository.CampaignRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outreach campaigns.
 *
 * <p>Launching resolves the segment and raises one event per recipient; the
 * notification module turns those into messages using the named template. The
 * campaign therefore does not know or care which channel is used, and switching
 * a campaign from SMS to email is a template change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final SegmentService segmentService;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<CampaignResponse> search(CampaignStatus status, UUID branchId,
                                                 Pageable pageable) {
        return PageResponse.of(campaignRepository.search(status, branchId, pageable),
                CampaignResponse::from);
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(UUID id) {
        return CampaignResponse.from(requireCampaign(id));
    }

    @Transactional
    public CampaignResponse create(CrmRequests.CampaignRequest request) {
        if (campaignRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Campaign code already exists: " + request.code());
        }
        if (request.startsOn() != null && request.endsOn() != null
                && request.endsOn().isBefore(request.startsOn())) {
            throw new ValidationException("A campaign cannot end before it starts");
        }

        Campaign campaign = new Campaign();
        campaign.setCode(request.code().trim().toUpperCase());
        apply(campaign, request);
        campaign.setStatus(CampaignStatus.DRAFT);

        Campaign saved = campaignRepository.save(campaign);
        auditService.record("CAMPAIGN_CREATED", "Campaign", saved.getId(), null,
                CampaignResponse.from(saved), saved.getBranchId());
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignResponse update(UUID id, CrmRequests.CampaignRequest request) {
        Campaign campaign = requireCampaign(id);
        if (!campaign.getStatus().isEditable()) {
            throw new ConflictException("A " + campaign.getStatus()
                    + " campaign can no longer be edited");
        }
        apply(campaign, request);
        return CampaignResponse.from(campaign);
    }

    /** Shows who a campaign would reach, without sending anything. */
    @Transactional(readOnly = true)
    public List<UUID> preview(UUID id) {
        Campaign campaign = requireCampaign(id);
        if (campaign.getSegmentId() == null) {
            throw new ValidationException("This campaign has no segment to resolve");
        }
        return segmentService.evaluate(campaign.getSegmentId());
    }

    /**
     * Resolves the audience and queues a message for each recipient.
     *
     * <p>A campaign can only be launched once: re-running it would message every
     * customer a second time.
     */
    @Transactional
    public CampaignResponse launch(UUID id) {
        Campaign campaign = requireCampaign(id);
        if (campaign.getStatus() != CampaignStatus.DRAFT
                && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new ConflictException("Campaign " + campaign.getCode() + " is "
                    + campaign.getStatus() + " and cannot be launched again");
        }
        if (campaign.getSegmentId() == null) {
            throw new ValidationException("A campaign needs a segment before it can be launched");
        }

        List<UUID> recipients = segmentService.evaluate(campaign.getSegmentId());
        campaign.setTargetCount(recipients.size());
        campaign.setLaunchedAt(Instant.now());

        for (UUID customerId : recipients) {
            // The notification module owns wording and channel; the campaign only
            // says who to reach and which template to use.
            events.publish(new DomainEvents.CampaignTargeted(campaign.getId(), customerId,
                    campaign.getBranchId(), campaign.getCode(), campaign.getName(),
                    campaign.getTemplateCode()));
        }

        campaign.setSentCount(recipients.size());
        campaign.setStatus(CampaignStatus.RUNNING);

        auditService.record("CAMPAIGN_LAUNCHED", "Campaign", id, null,
                Map.of("recipients", recipients.size(),
                        "templateCode", String.valueOf(campaign.getTemplateCode())),
                campaign.getBranchId());
        log.info("Campaign {} targeted {} customer(s)", campaign.getCode(), recipients.size());
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public CampaignResponse complete(UUID id) {
        Campaign campaign = requireCampaign(id);
        if (campaign.getStatus() != CampaignStatus.RUNNING) {
            throw new ConflictException("Only a running campaign can be completed");
        }
        campaign.setStatus(CampaignStatus.COMPLETED);
        campaign.setCompletedAt(Instant.now());
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public CampaignResponse cancel(UUID id, String reason) {
        Campaign campaign = requireCampaign(id);
        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new ConflictException("A completed campaign cannot be cancelled");
        }
        campaign.setStatus(CampaignStatus.CANCELLED);
        auditService.record("CAMPAIGN_CANCELLED", "Campaign", id, null,
                Map.of("reason", String.valueOf(reason)), campaign.getBranchId());
        return CampaignResponse.from(campaign);
    }

    private Campaign requireCampaign(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> NotFoundException.of("Campaign", id));
    }

    private void apply(Campaign campaign, CrmRequests.CampaignRequest request) {
        campaign.setName(request.name().trim());
        campaign.setDescription(request.description());
        campaign.setSegmentId(request.segmentId());
        campaign.setTemplateCode(request.templateCode());
        campaign.setBranchId(request.branchId());
        campaign.setStartsOn(request.startsOn());
        campaign.setEndsOn(request.endsOn());
    }
}
