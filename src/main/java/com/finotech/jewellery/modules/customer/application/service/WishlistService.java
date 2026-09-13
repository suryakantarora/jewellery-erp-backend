package com.finotech.jewellery.modules.customer.application.service;

import com.finotech.jewellery.modules.customer.api.request.WishlistEntryRequest;
import com.finotech.jewellery.modules.customer.api.response.WishlistEntryResponse;
import com.finotech.jewellery.modules.customer.domain.entity.CustomerWishlistEntry;
import com.finotech.jewellery.modules.customer.infrastructure.repository.CustomerRepository;
import com.finotech.jewellery.modules.customer.infrastructure.repository.CustomerWishlistRepository;
import com.finotech.jewellery.modules.inventory.application.ItemDirectory;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's wishlist. Item, product and design details are resolved through
 * the owning modules' ports at read time so the list always shows the current
 * price and status rather than a copy taken when the entry was added.
 */
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final CustomerWishlistRepository wishlistRepository;
    private final CustomerRepository customerRepository;
    private final ItemDirectory itemDirectory;
    private final ProductCatalog productCatalog;
    private final AuditService auditService;

    /** Result of an add: the entry, and whether it was created or already there. */
    public record AddResult(WishlistEntryResponse entry, boolean created) {
    }

    @Transactional(readOnly = true)
    public List<WishlistEntryResponse> list(UUID customerId) {
        requireCustomer(customerId);
        return enrich(wishlistRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId));
    }

    /**
     * Adds an entry. Wishing for the same physical piece twice is not an error
     * — the existing entry is returned unchanged.
     */
    @Transactional
    public AddResult add(UUID customerId, WishlistEntryRequest request) {
        requireCustomer(customerId);
        if (request.jewelleryItemId() == null && request.productId() == null
                && request.designId() == null) {
            throw new ValidationException(
                    "A wishlist entry needs a jewelleryItemId, productId or designId");
        }
        if (request.branchId() != null) {
            SecurityUtils.requireBranchAccess(request.branchId());
        }

        UUID productId = request.productId();
        UUID designId = request.designId();
        if (request.jewelleryItemId() != null) {
            var existing = wishlistRepository.findByCustomerIdAndJewelleryItemId(
                    customerId, request.jewelleryItemId());
            if (existing.isPresent()) {
                return new AddResult(enrich(List.of(existing.get())).get(0), false);
            }
            ItemDirectory.ItemSummary item = itemDirectory
                    .summariesFor(List.of(request.jewelleryItemId()))
                    .get(request.jewelleryItemId());
            if (item == null) {
                throw NotFoundException.of("JewelleryItem", request.jewelleryItemId());
            }
            // Fill in what the item already tells us so the entry survives the
            // piece being sold: the customer still wants "one like that".
            productId = productId != null ? productId : item.productId();
            designId = designId != null ? designId : item.designId();
        }
        if (productId != null) {
            productCatalog.requireProduct(productId);
        }
        if (designId != null && !productCatalog.designNamesFor(List.of(designId)).containsKey(designId)) {
            throw NotFoundException.of("JewelleryDesign", designId);
        }

        CustomerWishlistEntry entry = new CustomerWishlistEntry();
        entry.setCustomerId(customerId);
        entry.setJewelleryItemId(request.jewelleryItemId());
        entry.setProductId(productId);
        entry.setDesignId(designId);
        entry.setNote(request.note());
        entry.setBranchId(request.branchId());
        entry.setAddedBy(SecurityUtils.currentUsername().orElse("system"));

        CustomerWishlistEntry saved = wishlistRepository.saveAndFlush(entry);
        auditService.record("WISHLIST_ENTRY_ADDED", "CustomerWishlist", saved.getId(), null,
                Map.of("customerId", String.valueOf(customerId),
                        "jewelleryItemId", String.valueOf(request.jewelleryItemId()),
                        "productId", String.valueOf(productId),
                        "designId", String.valueOf(designId)),
                request.branchId());
        return new AddResult(enrich(List.of(saved)).get(0), true);
    }

    @Transactional
    public void remove(UUID customerId, UUID entryId) {
        CustomerWishlistEntry entry = wishlistRepository.findByIdAndCustomerId(entryId, customerId)
                .orElseThrow(() -> NotFoundException.of("WishlistEntry", entryId));
        wishlistRepository.delete(entry);
        auditService.record("WISHLIST_ENTRY_REMOVED", "CustomerWishlist", entryId, null,
                Map.of("customerId", String.valueOf(customerId)), entry.getBranchId());
    }

    // ---------- helpers ----------

    private void requireCustomer(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw NotFoundException.of("Customer", customerId);
        }
    }

    private List<WishlistEntryResponse> enrich(List<CustomerWishlistEntry> entries) {
        List<UUID> itemIds = entries.stream()
                .map(CustomerWishlistEntry::getJewelleryItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ItemDirectory.ItemSummary> items = itemDirectory.summariesFor(itemIds);

        Set<UUID> productIds = new HashSet<>();
        Set<UUID> designIds = new HashSet<>();
        for (CustomerWishlistEntry e : entries) {
            ItemDirectory.ItemSummary item = e.getJewelleryItemId() == null ? null
                    : items.get(e.getJewelleryItemId());
            UUID productId = e.getProductId() != null ? e.getProductId()
                    : item == null ? null : item.productId();
            UUID designId = e.getDesignId() != null ? e.getDesignId()
                    : item == null ? null : item.designId();
            if (productId != null) {
                productIds.add(productId);
            }
            if (designId != null) {
                designIds.add(designId);
            }
        }
        Map<UUID, ProductCatalog.ProductLabel> productLabels = productCatalog.labelsFor(productIds);
        Map<UUID, String> designNames = productCatalog.designNamesFor(designIds);

        return entries.stream().map(e -> {
            ItemDirectory.ItemSummary item = e.getJewelleryItemId() == null ? null
                    : items.get(e.getJewelleryItemId());
            UUID productId = e.getProductId() != null ? e.getProductId()
                    : item == null ? null : item.productId();
            UUID designId = e.getDesignId() != null ? e.getDesignId()
                    : item == null ? null : item.designId();
            ProductCatalog.ProductLabel label = productId == null ? null : productLabels.get(productId);
            String productName = label == null ? null : label.name();
            String designName = designId == null ? null : designNames.get(designId);
            return new WishlistEntryResponse(e.getId(), e.getCustomerId(), e.getJewelleryItemId(),
                    productId, designId,
                    item == null ? null : item.itemCode(),
                    productName, designName,
                    item == null ? null : item.currentPrice(),
                    item == null ? null : item.currency(),
                    item == null ? null : item.status(),
                    item == null ? null : item.primaryImageKey(),
                    e.getNote(), e.getAddedBy(), e.getCreatedAt());
        }).toList();
    }
}
