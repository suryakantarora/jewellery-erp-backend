package com.finotech.jewellery.modules.gemstone.application.service;

import com.finotech.jewellery.modules.gemstone.api.request.CertificateRequest;
import com.finotech.jewellery.modules.gemstone.api.request.GemstoneRequest;
import com.finotech.jewellery.modules.gemstone.api.response.CertificateResponse;
import com.finotech.jewellery.modules.gemstone.api.response.GemstoneResponse;
import com.finotech.jewellery.modules.gemstone.api.response.StoneResponse;
import com.finotech.jewellery.modules.gemstone.application.StoneRegistry;
import com.finotech.jewellery.modules.gemstone.domain.entity.Gemstone;
import com.finotech.jewellery.modules.gemstone.domain.entity.JewelleryStone;
import com.finotech.jewellery.modules.gemstone.domain.entity.StoneCertificate;
import com.finotech.jewellery.modules.gemstone.domain.enums.StoneSettingType;
import com.finotech.jewellery.modules.gemstone.domain.enums.StoneShape;
import com.finotech.jewellery.modules.gemstone.infrastructure.repository.GemstoneRepository;
import com.finotech.jewellery.modules.gemstone.infrastructure.repository.JewelleryStoneRepository;
import com.finotech.jewellery.modules.gemstone.infrastructure.repository.StoneCertificateRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Gemstone master data, certificates and the stones set in individual items.
 */
@Service
@RequiredArgsConstructor
public class GemstoneService implements StoneRegistry {

    private final GemstoneRepository gemstoneRepository;
    private final StoneCertificateRepository certificateRepository;
    private final JewelleryStoneRepository stoneRepository;
    private final AuditService auditService;

    // ---------- gemstone master ----------

    @Transactional(readOnly = true)
    public List<GemstoneResponse> listGemstones() {
        return gemstoneRepository.findAllByOrderByNameAsc().stream()
                .map(GemstoneResponse::from).toList();
    }

    @Transactional
    public GemstoneResponse createGemstone(GemstoneRequest request) {
        if (gemstoneRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Gemstone code already exists: " + request.code());
        }
        Gemstone gemstone = new Gemstone();
        applyGemstone(gemstone, request);
        Gemstone saved = gemstoneRepository.save(gemstone);
        auditService.record("GEMSTONE_CREATED", "Gemstone", saved.getId(), null,
                GemstoneResponse.from(saved));
        return GemstoneResponse.from(saved);
    }

    @Transactional
    public GemstoneResponse updateGemstone(UUID id, GemstoneRequest request) {
        Gemstone gemstone = gemstoneRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Gemstone", id));
        GemstoneResponse before = GemstoneResponse.from(gemstone);
        applyGemstone(gemstone, request);
        GemstoneResponse after = GemstoneResponse.from(gemstone);
        auditService.record("GEMSTONE_UPDATED", "Gemstone", id, before, after);
        return after;
    }

    // ---------- certificates ----------

    @Transactional(readOnly = true)
    public PageResponse<CertificateResponse> searchCertificates(String search, Pageable pageable) {
        return PageResponse.of(certificateRepository.search(search, pageable), CertificateResponse::from);
    }

    @Transactional
    public CertificateResponse createCertificate(CertificateRequest request) {
        if (certificateRepository.existsByCertificateNumberIgnoreCase(request.certificateNumber())) {
            throw new ConflictException("Certificate already registered: " + request.certificateNumber());
        }
        StoneCertificate certificate = new StoneCertificate();
        certificate.setCertificateNumber(request.certificateNumber().trim());
        certificate.setIssuingLab(request.issuingLab().trim());
        certificate.setIssueDate(request.issueDate());
        certificate.setStorageKey(request.storageKey());
        certificate.setVerificationUrl(request.verificationUrl());
        certificate.setNotes(request.notes());
        StoneCertificate saved = certificateRepository.save(certificate);
        auditService.record("CERTIFICATE_CREATED", "StoneCertificate", saved.getId(), null,
                CertificateResponse.from(saved));
        return CertificateResponse.from(saved);
    }

    // ---------- stones on an item ----------

    @Transactional(readOnly = true)
    public List<StoneResponse> stonesOfItem(UUID jewelleryItemId) {
        return stoneRepository.findAllByJewelleryItemId(jewelleryItemId).stream()
                .map(StoneResponse::from).toList();
    }

    @Override
    @Transactional
    public StoneTotals replaceStonesOf(UUID jewelleryItemId, List<StoneSpec> stones) {
        stoneRepository.deleteAllByJewelleryItemId(jewelleryItemId);
        if (stones == null || stones.isEmpty()) {
            return new StoneTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<JewelleryStone> rows = stones.stream()
                .map(spec -> toEntity(jewelleryItemId, spec))
                .toList();
        stoneRepository.saveAll(rows);
        return totals(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public StoneTotals totalsFor(UUID jewelleryItemId) {
        return totals(stoneRepository.findAllByJewelleryItemId(jewelleryItemId));
    }

    @Override
    @Transactional
    public void removeStonesOf(UUID jewelleryItemId) {
        stoneRepository.deleteAllByJewelleryItemId(jewelleryItemId);
    }

    // ---------- helpers ----------

    private StoneTotals totals(List<JewelleryStone> rows) {
        int count = rows.stream().mapToInt(JewelleryStone::getStoneCount).sum();
        BigDecimal carat = rows.stream()
                .map(s -> MoneyUtils.nullSafe(s.getCaratWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grams = rows.stream()
                .map(s -> MoneyUtils.nullSafe(s.getWeightGrams()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = rows.stream()
                .map(s -> MoneyUtils.nullSafe(s.getStoneValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StoneTotals(count, carat, MoneyUtils.weight(grams), MoneyUtils.money(value));
    }

    private JewelleryStone toEntity(UUID itemId, StoneSpec spec) {
        JewelleryStone stone = new JewelleryStone();
        stone.setJewelleryItemId(itemId);
        stone.setGemstone(gemstoneRepository.findById(spec.gemstoneId())
                .orElseThrow(() -> NotFoundException.of("Gemstone", spec.gemstoneId())));
        if (spec.certificateId() != null) {
            stone.setCertificate(certificateRepository.findById(spec.certificateId())
                    .orElseThrow(() -> NotFoundException.of("StoneCertificate", spec.certificateId())));
        }
        stone.setStoneCount(Math.max(spec.stoneCount(), 1));
        stone.setCaratWeight(spec.caratWeight());
        stone.setShape(StringUtils.hasText(spec.shape())
                ? StoneShape.valueOf(spec.shape().toUpperCase()) : null);
        stone.setCut(spec.cut());
        stone.setColour(spec.colour());
        stone.setClarity(spec.clarity());
        stone.setSettingType(StringUtils.hasText(spec.settingType())
                ? StoneSettingType.valueOf(spec.settingType().toUpperCase()) : null);
        stone.setRatePerCarat(spec.ratePerCarat());
        stone.setStoneValue(spec.stoneValue());
        stone.setWeightGrams(spec.weightGrams());
        stone.setNotes(spec.notes());
        return stone;
    }

    private void applyGemstone(Gemstone gemstone, GemstoneRequest request) {
        gemstone.setCode(request.code().trim().toUpperCase());
        gemstone.setName(request.name().trim());
        gemstone.setDiamond(request.diamond());
        gemstone.setPrecious(request.precious());
        gemstone.setDescription(request.description());
    }
}
