package com.finotech.jewellery.modules.crm.application.service;

import com.finotech.jewellery.modules.crm.api.request.CrmRequests;
import com.finotech.jewellery.modules.crm.api.response.CrmResponses.SegmentResponse;
import com.finotech.jewellery.modules.crm.domain.entity.CustomerSegment;
import com.finotech.jewellery.modules.crm.infrastructure.repository.CustomerSegmentRepository;
import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.loyalty.application.LoyaltyDirectory;
import com.finotech.jewellery.modules.sales.application.SalesHistory;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer segmentation.
 *
 * <p>Segments are evaluated rather than stored as fixed membership lists, so a
 * campaign always goes to who qualifies today. Evaluation pulls candidates from
 * the customer module, then filters them against purchase history and loyalty
 * standing — each through that module's published contract, in one batch rather
 * than one query per customer.
 */
@Service
@RequiredArgsConstructor
public class SegmentService {

    private final CustomerSegmentRepository segmentRepository;
    private final CustomerDirectory customerDirectory;
    private final SalesHistory salesHistory;
    private final LoyaltyDirectory loyaltyDirectory;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SegmentResponse> list() {
        return segmentRepository.findAllByOrderByCodeAsc().stream()
                .map(SegmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SegmentResponse get(UUID id) {
        return SegmentResponse.from(requireSegment(id));
    }

    @Transactional
    public SegmentResponse create(CrmRequests.SegmentRequest request) {
        if (segmentRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Segment code already exists: " + request.code());
        }
        if (isEmptyCriteria(request)) {
            throw new ValidationException(
                    "A segment needs at least one criterion, otherwise it selects everyone");
        }

        CustomerSegment segment = new CustomerSegment();
        segment.setCode(request.code().trim().toUpperCase());
        apply(segment, request);

        CustomerSegment saved = segmentRepository.save(segment);
        auditService.record("SEGMENT_CREATED", "CustomerSegment", saved.getId(), null,
                SegmentResponse.from(saved));
        return SegmentResponse.from(saved);
    }

    @Transactional
    public SegmentResponse update(UUID id, CrmRequests.SegmentRequest request) {
        CustomerSegment segment = requireSegment(id);
        SegmentResponse before = SegmentResponse.from(segment);
        apply(segment, request);
        auditService.record("SEGMENT_UPDATED", "CustomerSegment", id, before,
                SegmentResponse.from(segment));
        return SegmentResponse.from(segment);
    }

    /**
     * Resolves the customers who qualify for a segment right now.
     */
    @Transactional(readOnly = true)
    public List<UUID> evaluate(UUID segmentId) {
        return evaluate(requireSegment(segmentId));
    }

    @Transactional(readOnly = true)
    public List<UUID> evaluate(CustomerSegment segment) {
        List<CustomerDirectory.CustomerView> candidates =
                customerDirectory.findSegmentCandidates(segment.getBranchId(),
                        segment.getBirthdayMonth());
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream()
                .map(CustomerDirectory.CustomerView::id).toList();

        boolean needsPurchaseData = segment.getMinLifetimeSpend() != null
                || segment.getMinPurchaseCount() != null
                || segment.getInactiveDays() != null;
        Map<UUID, SalesHistory.PurchaseSummary> purchases = needsPurchaseData
                ? salesHistory.summariesFor(candidateIds)
                : Map.of();

        List<UUID> matched = new ArrayList<>();
        for (CustomerDirectory.CustomerView candidate : candidates) {
            if (!matchesPurchaseCriteria(segment, purchases.get(candidate.id()))) {
                continue;
            }
            if (!matchesTier(segment, candidate.id())) {
                continue;
            }
            matched.add(candidate.id());
        }
        return matched;
    }

    // ---------- criteria ----------

    private boolean matchesPurchaseCriteria(CustomerSegment segment,
                                            SalesHistory.PurchaseSummary summary) {
        if (segment.getMinLifetimeSpend() == null && segment.getMinPurchaseCount() == null
                && segment.getInactiveDays() == null) {
            return true;
        }
        if (summary == null) {
            // Never purchased. That qualifies only for an "inactive" segment, and
            // only if no positive spend or count is also required.
            return segment.getInactiveDays() != null
                    && segment.getMinLifetimeSpend() == null
                    && segment.getMinPurchaseCount() == null;
        }
        if (segment.getMinLifetimeSpend() != null
                && summary.lifetimeSpend().compareTo(segment.getMinLifetimeSpend()) < 0) {
            return false;
        }
        if (segment.getMinPurchaseCount() != null
                && summary.purchaseCount() < segment.getMinPurchaseCount()) {
            return false;
        }
        if (segment.getInactiveDays() != null) {
            LocalDate cutoff = LocalDate.now().minusDays(segment.getInactiveDays());
            if (summary.lastPurchaseDate() != null && summary.lastPurchaseDate().isAfter(cutoff)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesTier(CustomerSegment segment, UUID customerId) {
        if (segment.getTierCode() == null) {
            return true;
        }
        LoyaltyDirectory.AccountView account = loyaltyDirectory.findAccount(customerId);
        return account != null && segment.getTierCode().equalsIgnoreCase(account.tierCode());
    }

    private boolean isEmptyCriteria(CrmRequests.SegmentRequest request) {
        return request.branchId() == null && request.tierCode() == null
                && request.minLifetimeSpend() == null && request.minPurchaseCount() == null
                && request.inactiveDays() == null && request.birthdayMonth() == null;
    }

    private void apply(CustomerSegment segment, CrmRequests.SegmentRequest request) {
        segment.setName(request.name().trim());
        segment.setDescription(request.description());
        segment.setBranchId(request.branchId());
        segment.setTierCode(request.tierCode() == null
                ? null : request.tierCode().trim().toUpperCase());
        segment.setMinLifetimeSpend(request.minLifetimeSpend());
        segment.setMinPurchaseCount(request.minPurchaseCount());
        segment.setInactiveDays(request.inactiveDays());
        segment.setBirthdayMonth(request.birthdayMonth());
    }

    CustomerSegment requireSegment(UUID id) {
        return segmentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("CustomerSegment", id));
    }
}
