package com.finotech.jewellery.modules.catalogue.application.service;

import com.finotech.jewellery.modules.catalogue.api.response.CatalogueItemResponse;
import com.finotech.jewellery.modules.catalogue.infrastructure.repository.CatalogueQueryRepository;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer-facing view of stock.
 *
 * <p>Prices come from {@link PricingCalculator}, the same engine a sale uses, so
 * the figure quoted across the counter is the figure that will be charged. The
 * alternative — a stored price on the item — goes stale the moment the gold
 * rate moves, and quoting a stale price to a customer is a promise the shop
 * then has to keep or break.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueService {

    private final CatalogueQueryRepository catalogue;
    private final PricingCalculator pricing;

    @Transactional(readOnly = true)
    public PageResponse<CatalogueItemResponse> search(UUID branchId, UUID categoryId, UUID metalId,
                                                      UUID purityId, String search,
                                                      Pageable pageable) {
        UUID scoped = resolveBranch(branchId);
        UUID companyId = SecurityUtils.currentCompanyIdOrNull();

        List<CatalogueQueryRepository.Row> rows = catalogue.search(
                companyId, scoped, categoryId, metalId, purityId, search,
                pageable.getPageSize(), (int) pageable.getOffset());

        // Artwork fallback resolved once per page, one query per type, the
        // same way primaryImageKey and display names are batched elsewhere.
        Map<UUID, String> designArt = catalogue.primaryDesignImageKeys(
                rows.stream().map(CatalogueQueryRepository.Row::designId).toList());
        Map<UUID, String> productArt = catalogue.primaryProductImageKeys(
                rows.stream().map(CatalogueQueryRepository.Row::productId).toList());

        List<CatalogueItemResponse> content = rows.stream()
                .map(row -> toResponse(row, scoped, fallbackArtwork(row, designArt, productArt)))
                .toList();

        long total = catalogue.count(companyId, scoped, categoryId, metalId, purityId, search);
        return PageResponse.of(new PageImpl<>(content, pageable, total));
    }

    /**
     * Confines the catalogue to a branch the caller actually works in.
     *
     * <p>Elsewhere in the platform {@code branchId} is a filter rather than a
     * boundary — reads are not branch-scoped, which is defensible for staff who
     * legitimately ask "do we have this in Pakse?". This surface is different:
     * it is the one written to be shown outside the business, and a shop must
     * never quote a customer a piece from a branch it cannot hand them.
     *
     * <p>Omitting the parameter means "where I am", not "everything".
     */
    private UUID resolveBranch(UUID requested) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (requested == null) {
            // A super admin genuinely has no home branch, so they see the lot.
            return user.superAdmin() || user.branchIds().isEmpty()
                    ? null
                    : user.branchIds().iterator().next();
        }
        if (!user.hasAccessToBranch(requested)) {
            throw new ForbiddenException("No access to branch " + requested);
        }
        return requested;
    }

    private static String fallbackArtwork(CatalogueQueryRepository.Row row,
                                          Map<UUID, String> designArt,
                                          Map<UUID, String> productArt) {
        String design = row.designId() == null ? null : designArt.get(row.designId());
        return design != null ? design : productArt.get(row.productId());
    }

    private CatalogueItemResponse toResponse(CatalogueQueryRepository.Row row, UUID branchId,
                                             String fallbackImageKey) {
        BigDecimal price = null;
        String currency = row.currency();
        try {
            // No customer, so no loyalty tier discount: a catalogue shows the
            // list price. What an individual customer pays is settled at the
            // till, where the tier is known.
            PricingCalculator.PriceBreakdown breakdown = pricing.calculate(
                    new PricingCalculator.PriceRequest(
                            row.id(), null, branchId, null, null, false, false));
            price = breakdown.finalPrice();
            currency = breakdown.currency();
        } catch (RuntimeException ex) {
            // A missing metal rate must not blank the whole catalogue. The row
            // is still worth showing — the piece exists and can be described —
            // so the price is left null and the client says so.
            log.debug("Could not price {}: {}", row.itemCode(), ex.getMessage());
        }

        return new CatalogueItemResponse(row.id(), row.itemCode(), row.productName(),
                row.categoryName(), row.typeName(), row.metalName(), row.purityCode(),
                row.purityName(), row.grossWeight(), row.netMetalWeight(), row.stoneCount(),
                row.totalCarat(), row.hallmarkNumber(), row.primaryImageKey(),
                row.productId(), row.designId(), fallbackImageKey, price, currency);
    }
}
