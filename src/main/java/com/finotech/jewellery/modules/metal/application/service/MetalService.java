package com.finotech.jewellery.modules.metal.application.service;

import com.finotech.jewellery.modules.metal.api.request.MetalRequest;
import com.finotech.jewellery.modules.metal.api.request.PurityRequest;
import com.finotech.jewellery.modules.metal.api.response.MetalResponse;
import com.finotech.jewellery.modules.metal.api.response.PurityResponse;
import com.finotech.jewellery.modules.metal.domain.entity.Metal;
import com.finotech.jewellery.modules.metal.domain.entity.Purity;
import com.finotech.jewellery.modules.metal.infrastructure.repository.MetalRepository;
import com.finotech.jewellery.modules.metal.infrastructure.repository.PurityRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Metal and purity master data.
 */
@Service
@RequiredArgsConstructor
public class MetalService {

    private final MetalRepository metalRepository;
    private final PurityRepository purityRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MetalResponse> listMetals() {
        return metalRepository.findAllByOrderByNameAsc().stream().map(MetalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MetalResponse getMetal(UUID id) {
        return MetalResponse.from(requireMetal(id));
    }

    @Transactional
    public MetalResponse createMetal(MetalRequest request) {
        if (metalRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Metal code already exists: " + request.code());
        }
        Metal metal = new Metal();
        applyMetal(metal, request);
        Metal saved = metalRepository.save(metal);
        auditService.record("METAL_CREATED", "Metal", saved.getId(), null, MetalResponse.from(saved));
        return MetalResponse.from(saved);
    }

    @Transactional
    public MetalResponse updateMetal(UUID id, MetalRequest request) {
        Metal metal = requireMetal(id);
        MetalResponse before = MetalResponse.from(metal);
        applyMetal(metal, request);
        MetalResponse after = MetalResponse.from(metal);
        auditService.record("METAL_UPDATED", "Metal", id, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<PurityResponse> listPurities(UUID metalId) {
        return purityRepository.findAllByMetalIdOrderByDisplayOrderAscCodeAsc(metalId).stream()
                .map(PurityResponse::from).toList();
    }

    @Transactional
    public PurityResponse createPurity(PurityRequest request) {
        Metal metal = requireMetal(request.metalId());
        if (purityRepository.existsByMetalIdAndCodeIgnoreCase(metal.getId(), request.code())) {
            throw new ConflictException("Purity already exists for this metal: " + request.code());
        }
        Purity purity = new Purity();
        purity.setMetal(metal);
        purity.setCode(request.code().trim().toUpperCase());
        purity.setName(request.name().trim());
        purity.setFineness(request.fineness());
        purity.setDisplayOrder(request.displayOrder());
        Purity saved = purityRepository.save(purity);
        auditService.record("PURITY_CREATED", "Purity", saved.getId(), null, PurityResponse.from(saved));
        return PurityResponse.from(saved);
    }

    @Transactional
    public PurityResponse updatePurity(UUID id, PurityRequest request) {
        Purity purity = purityRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Purity", id));
        PurityResponse before = PurityResponse.from(purity);
        purity.setName(request.name().trim());
        purity.setFineness(request.fineness());
        purity.setDisplayOrder(request.displayOrder());
        PurityResponse after = PurityResponse.from(purity);
        auditService.record("PURITY_UPDATED", "Purity", id, before, after);
        return after;
    }

    private Metal requireMetal(UUID id) {
        return metalRepository.findById(id).orElseThrow(() -> NotFoundException.of("Metal", id));
    }

    private void applyMetal(Metal metal, MetalRequest request) {
        metal.setCode(request.code().trim().toUpperCase());
        metal.setName(request.name().trim());
        metal.setSymbol(request.symbol());
        metal.setDescription(request.description());
        if (StringUtils.hasText(request.weightUnit())) {
            metal.setWeightUnit(request.weightUnit().toUpperCase());
        }
    }
}
