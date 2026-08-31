package com.finotech.jewellery.modules.loyalty.application.service;

import com.finotech.jewellery.modules.loyalty.api.request.LoyaltyRequests;
import com.finotech.jewellery.modules.loyalty.api.response.LoyaltyResponses.ProgramResponse;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyProgram;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyTier;
import com.finotech.jewellery.modules.loyalty.infrastructure.repository.LoyaltyProgramRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loyalty program and tier configuration. Changing the earn rate affects future
 * awards only; points already granted keep the value they were granted at.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyProgramService {

    private final LoyaltyProgramRepository programRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ProgramResponse> list() {
        return programRepository.findAllByOrderByCodeAsc().stream()
                .map(ProgramResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProgramResponse get(UUID id) {
        return ProgramResponse.from(programRepository.findWithTiersById(id)
                .orElseThrow(() -> NotFoundException.of("LoyaltyProgram", id)));
    }

    @Transactional
    public ProgramResponse create(LoyaltyRequests.ProgramRequest request) {
        if (programRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Program code already exists: " + request.code());
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new ValidationException("effectiveTo cannot be before effectiveFrom");
        }

        LoyaltyProgram program = new LoyaltyProgram();
        program.setCode(request.code().trim().toUpperCase());
        program.setName(request.name().trim());
        program.setCompanyId(request.companyId());
        program.setPointsPerCurrencyUnit(request.pointsPerCurrencyUnit());
        program.setCurrencyValuePerPoint(request.currencyValuePerPoint());
        program.setPointsValidityMonths(request.pointsValidityMonths());
        program.setMinimumRedeemablePoints(request.minimumRedeemablePoints());
        program.setEarnOnMakingChargeOnly(request.earnOnMakingChargeOnly());
        program.setEffectiveFrom(request.effectiveFrom());
        program.setEffectiveTo(request.effectiveTo());

        Set<String> codes = new HashSet<>();
        Set<Long> thresholds = new HashSet<>();
        for (LoyaltyRequests.TierRequest tierRequest : request.tiers()) {
            if (!codes.add(tierRequest.code().toUpperCase())) {
                throw new ValidationException("Duplicate tier code: " + tierRequest.code());
            }
            // Two tiers at the same threshold would make the resulting tier
            // depend on row order, which is not a rule anyone could explain.
            if (!thresholds.add(tierRequest.minimumPoints())) {
                throw new ValidationException(
                        "Two tiers cannot share the threshold " + tierRequest.minimumPoints());
            }
            LoyaltyTier tier = new LoyaltyTier();
            tier.setCode(tierRequest.code().trim().toUpperCase());
            tier.setName(tierRequest.name().trim());
            tier.setMinimumPoints(tierRequest.minimumPoints());
            tier.setEarnMultiplier(tierRequest.earnMultiplier());
            tier.setDiscountPercentage(tierRequest.discountPercentage());
            tier.setDisplayOrder(tierRequest.displayOrder());
            tier.setBenefits(tierRequest.benefits());
            program.addTier(tier);
        }

        LoyaltyProgram saved = programRepository.save(program);
        auditService.record("LOYALTY_PROGRAM_CREATED", "LoyaltyProgram", saved.getId(), null,
                ProgramResponse.from(saved));
        return ProgramResponse.from(saved);
    }

    @Transactional
    public ProgramResponse setActive(UUID id, boolean active) {
        LoyaltyProgram program = programRepository.findWithTiersById(id)
                .orElseThrow(() -> NotFoundException.of("LoyaltyProgram", id));
        program.setActive(active);
        auditService.record("LOYALTY_PROGRAM_STATUS_CHANGED", "LoyaltyProgram", id, null,
                java.util.Map.of("active", active));
        return ProgramResponse.from(program);
    }
}
