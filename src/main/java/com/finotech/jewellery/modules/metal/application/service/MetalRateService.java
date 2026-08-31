package com.finotech.jewellery.modules.metal.application.service;

import com.finotech.jewellery.modules.metal.api.request.PublishRateRequest;
import com.finotech.jewellery.modules.metal.api.response.MetalRateResponse;
import com.finotech.jewellery.modules.metal.application.MetalRateProvider;
import com.finotech.jewellery.modules.metal.domain.entity.MetalRate;
import com.finotech.jewellery.modules.metal.domain.entity.Purity;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import com.finotech.jewellery.modules.metal.infrastructure.repository.MetalRateRepository;
import com.finotech.jewellery.modules.metal.infrastructure.repository.MetalRepository;
import com.finotech.jewellery.modules.metal.infrastructure.repository.PurityRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Publishes and reads daily metal rates.
 *
 * <p>Publishing the same metal/purity/date twice updates that day's rate rather
 * than creating an ambiguous duplicate, but never touches earlier days — prices
 * already captured on a sale are unaffected.
 */
@Service
@RequiredArgsConstructor
public class MetalRateService implements MetalRateProvider {

    private final MetalRateRepository rateRepository;
    private final MetalRepository metalRepository;
    private final PurityRepository purityRepository;
    private final AuditService auditService;

    @Transactional
    public MetalRateResponse publish(PublishRateRequest request) {
        Purity purity = purityRepository.findById(request.purityId())
                .orElseThrow(() -> NotFoundException.of("Purity", request.purityId()));
        if (!purity.getMetal().getId().equals(request.metalId())) {
            throw new ValidationException("Purity does not belong to the given metal");
        }
        if (request.effectiveDate().isAfter(LocalDate.now().plusDays(7))) {
            throw new ValidationException("Rates cannot be published more than 7 days ahead");
        }

        MetalRate rate = rateRepository
                .findFirstByMetalIdAndPurityIdAndRateTypeAndEffectiveDateAndBranchId(
                        request.metalId(), request.purityId(), request.rateType(),
                        request.effectiveDate(), request.branchId())
                .orElseGet(MetalRate::new);

        MetalRateResponse before = rate.getId() == null ? null : MetalRateResponse.from(rate);

        rate.setMetal(purity.getMetal());
        rate.setPurity(purity);
        rate.setRateType(request.rateType());
        rate.setEffectiveDate(request.effectiveDate());
        rate.setRatePerUnit(request.ratePerUnit());
        rate.setBranchId(request.branchId());
        rate.setNotes(request.notes());
        if (StringUtils.hasText(request.currency())) {
            rate.setCurrency(request.currency().toUpperCase());
        }
        rate.setPublishedAt(Instant.now());
        rate.setPublishedBy(SecurityUtils.currentUsername().orElse("system"));

        MetalRate saved = rateRepository.save(rate);
        auditService.record("METAL_RATE_PUBLISHED", "MetalRate", saved.getId(), before,
                MetalRateResponse.from(saved), request.branchId());
        return MetalRateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<MetalRateResponse> search(UUID metalId, UUID purityId, RateType rateType,
                                                  LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.of(rateRepository.search(metalId, purityId, rateType, from, to, pageable),
                MetalRateResponse::from);
    }

    @Transactional(readOnly = true)
    public MetalRateResponse currentRate(UUID metalId, UUID purityId, RateType rateType,
                                         LocalDate onDate, UUID branchId) {
        return MetalRateResponse.from(findEffective(metalId, purityId, rateType,
                onDate == null ? LocalDate.now() : onDate, branchId));
    }

    // ---------- cross-module provider ----------

    @Override
    @Transactional(readOnly = true)
    public RateView requireEffectiveRate(UUID metalId, UUID purityId, RateType rateType,
                                         LocalDate onDate, UUID branchId) {
        MetalRate rate = findEffective(metalId, purityId, rateType, onDate, branchId);
        return new RateView(rate.getId(), metalId, purityId, rate.getRateType(),
                rate.getEffectiveDate(), rate.getRatePerUnit(), rate.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal fineness(UUID purityId) {
        return purityRepository.findById(purityId)
                .orElseThrow(() -> NotFoundException.of("Purity", purityId))
                .getFineness();
    }

    private MetalRate findEffective(UUID metalId, UUID purityId, RateType rateType,
                                    LocalDate onDate, UUID branchId) {
        List<MetalRate> matches = rateRepository
                .findEffectiveRates(metalId, purityId, rateType, onDate, branchId, PageRequest.of(0, 1))
                .getContent();
        if (matches.isEmpty()) {
            throw new NotFoundException("No " + rateType + " rate published for metal " + metalId
                    + " purity " + purityId + " on or before " + onDate);
        }
        return matches.get(0);
    }
}
