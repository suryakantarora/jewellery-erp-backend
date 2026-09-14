package com.finotech.jewellery.modules.inventory.application.service;

import com.finotech.jewellery.modules.gemstone.application.StoneRegistry;
import com.finotech.jewellery.modules.gemstone.application.service.GemstoneService;
import com.finotech.jewellery.modules.warehouse.application.BinDirectory;
import com.finotech.jewellery.modules.inventory.api.request.LinkImageRequest;
import com.finotech.jewellery.modules.inventory.api.request.AssignBinRequest;
import com.finotech.jewellery.modules.inventory.api.request.ChangeStatusRequest;
import com.finotech.jewellery.modules.inventory.api.request.CreateItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.ReserveItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.ResolveTagsRequest;
import com.finotech.jewellery.modules.inventory.api.request.TagItemRequest;
import com.finotech.jewellery.modules.inventory.api.request.UpdateImageRequest;
import com.finotech.jewellery.modules.inventory.api.request.UpdateItemRequest;
import com.finotech.jewellery.modules.inventory.api.response.ItemImageResponse;
import com.finotech.jewellery.modules.inventory.api.response.ItemPassportResponse;
import com.finotech.jewellery.modules.inventory.api.response.JewelleryItemResponse;
import com.finotech.jewellery.modules.inventory.api.response.LifecycleEventResponse;
import com.finotech.jewellery.modules.inventory.api.response.ProductAvailabilityResponse;
import com.finotech.jewellery.modules.inventory.api.response.TagResolutionResponse;
import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.inventory.domain.entity.ItemImage;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.LifecycleEventType;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.BranchStatusCount;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.ItemImageRepository;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.ItemLifecycleEventRepository;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.JewelleryItemRepository;
import com.finotech.jewellery.modules.metal.application.MetalRateProvider;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.modules.organization.application.OrganizationNames;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The serialized jewellery item: creation, tagging, reservation, status and the
 * digital passport. Inventory is the source of truth for physical status and
 * location (dependency rule 2).
 */
@Service
@RequiredArgsConstructor
public class JewelleryItemService implements InventoryOperations {

    private static final int DEFAULT_HOLD_HOURS = 24;

    private final JewelleryItemRepository itemRepository;
    private final ItemLifecycleEventRepository lifecycleRepository;
    private final ItemLifecycleRecorder lifecycle;
    private final ProductCatalog productCatalog;
    private final OrganizationDirectory organizationDirectory;
    private final MetalRateProvider metalRateProvider;
    private final GemstoneService stoneRegistry;
    private final ItemImageRepository imageRepository;
    private final BinDirectory binDirectory;
    private final OrganizationNames organizationNames;
    private final ItemDisplayNameResolver displayNames;
    private final AuditService auditService;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public PageResponse<JewelleryItemResponse> search(String search, UUID productId, ItemStatus status,
                                                      UUID locationId, UUID branchId, UUID metalId,
                                                      UUID purityId, UUID binId,
                                                      Pageable pageable) {
        return search(search, productId, status, locationId, branchId, metalId, purityId, binId,
                null, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<JewelleryItemResponse> search(String search, UUID productId, ItemStatus status,
                                                      UUID locationId, UUID branchId, UUID metalId,
                                                      UUID purityId, UUID binId,
                                                      BigDecimal minPrice, BigDecimal maxPrice,
                                                      Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ValidationException("minPrice must not exceed maxPrice");
        }
        Page<JewelleryItem> page = itemRepository.search(SecurityUtils.currentCompanyIdOrNull(), search,
                productId, status, locationId, branchId, metalId, purityId, binId, minPrice, maxPrice,
                pageable);
        JewelleryItemResponse.DisplayNames names = displayNames.resolve(page.getContent());
        return PageResponse.of(page, item -> JewelleryItemResponse.from(item, names));
    }

    @Transactional(readOnly = true)
    public JewelleryItemResponse get(UUID id) {
        JewelleryItem item = requireItemEntity(id);
        // Another company's item is reported as absent, never as forbidden.
        UUID scope = SecurityUtils.currentCompanyIdOrNull();
        if (scope != null && item.getCurrentBranchId() != null
                && !organizationDirectory.companyOfBranch(item.getCurrentBranchId())
                        .map(scope::equals).orElse(true)) {
            throw NotFoundException.of("JewelleryItem", id);
        }
        return respond(item);
    }

    /** Resolves an item by any of its physical tags — used by POS scanners. */
    @Transactional(readOnly = true)
    public JewelleryItemResponse findByTag(String tag) {
        return itemRepository.findByRfidTag(tag)
                .or(() -> itemRepository.findByBarcode(tag))
                .or(() -> itemRepository.findByQrCode(tag))
                .or(() -> itemRepository.findByItemCodeIgnoreCase(tag))
                .map(this::respond)
                .orElseThrow(() -> new NotFoundException("No item found for tag: " + tag));
    }

    /**
     * Resolves a whole sweep of scanned tags at once.
     *
     * <p>One query fetches every candidate item; the tags are then matched back
     * in memory so the response says which tag found which item. Physical tags
     * match exactly, item codes case-insensitively, mirroring {@link #findByTag}.
     * Duplicate scans collapse to one entry, and the input order is kept so the
     * app can show results in the order the gate read them.
     */
    @Transactional(readOnly = true)
    public TagResolutionResponse resolveTags(ResolveTagsRequest request) {
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : request.tags()) {
            if (StringUtils.hasText(tag)) {
                tags.add(tag.trim());
            }
        }
        if (tags.isEmpty()) {
            throw new ValidationException("At least one tag is required");
        }
        List<String> upper = tags.stream().map(String::toUpperCase).toList();
        List<JewelleryItem> items = itemRepository.findAllByAnyTag(tags, upper);
        JewelleryItemResponse.DisplayNames names = displayNames.resolve(items);

        Map<String, JewelleryItem> byExactTag = new HashMap<>();
        Map<String, JewelleryItem> byItemCode = new HashMap<>();
        for (JewelleryItem item : items) {
            if (item.getRfidTag() != null) {
                byExactTag.put(item.getRfidTag(), item);
            }
            if (item.getQrCode() != null) {
                byExactTag.put(item.getQrCode(), item);
            }
            if (item.getBarcode() != null) {
                byExactTag.put(item.getBarcode(), item);
            }
            byItemCode.put(item.getItemCode().toUpperCase(), item);
        }

        List<TagResolutionResponse.ResolvedTag> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String tag : tags) {
            JewelleryItem match = byExactTag.get(tag);
            if (match == null) {
                match = byItemCode.get(tag.toUpperCase());
            }
            if (match == null) {
                unresolved.add(tag);
            } else {
                resolved.add(new TagResolutionResponse.ResolvedTag(tag,
                        JewelleryItemResponse.from(match, names)));
            }
        }
        return new TagResolutionResponse(resolved, unresolved);
    }

    /**
     * Stock of one product per branch, limited to the branches the caller may
     * see: every active branch for a super administrator, otherwise the user's
     * own. A branch the caller can see but that holds nothing is still listed
     * with zeros — "0 in Pakse" is the answer the sales floor is asking for.
     */
    @Transactional(readOnly = true)
    public ProductAvailabilityResponse availability(UUID productId) {
        ProductCatalog.ProductView product = productCatalog.requireProduct(productId);
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        List<OrganizationNames.BranchRef> branches = user.superAdmin()
                ? organizationNames.activeBranches()
                : organizationNames.branches(user.branchIds());
        if (branches.isEmpty()) {
            return new ProductAvailabilityResponse(product.id(), product.name(), List.of());
        }

        List<UUID> branchIds = branches.stream().map(OrganizationNames.BranchRef::id).toList();
        Map<UUID, long[]> counts = new HashMap<>();
        for (BranchStatusCount row : itemRepository.countByBranchAndStatus(productId, branchIds)) {
            long[] tally = counts.computeIfAbsent(row.branchId(), k -> new long[2]);
            if (row.status() == ItemStatus.AVAILABLE) {
                tally[0] += row.count();
            }
            if (row.status() != ItemStatus.SOLD && row.status() != ItemStatus.SCRAPPED) {
                tally[1] += row.count();
            }
        }

        List<ProductAvailabilityResponse.BranchAvailability> rows = branches.stream()
                .sorted(Comparator.comparing(OrganizationNames.BranchRef::name,
                        String.CASE_INSENSITIVE_ORDER))
                .map(b -> {
                    long[] tally = counts.getOrDefault(b.id(), new long[2]);
                    return new ProductAvailabilityResponse.BranchAvailability(
                            b.id(), b.name(), tally[0], tally[1]);
                })
                .toList();
        return new ProductAvailabilityResponse(product.id(), product.name(), rows);
    }

    @Transactional(readOnly = true)
    public ItemPassportResponse passport(UUID id) {
        JewelleryItem item = requireItemEntity(id);
        List<LifecycleEventResponse> history = lifecycleRepository
                .findAllByJewelleryItemIdOrderByOccurredAtAsc(id).stream()
                .map(LifecycleEventResponse::from).toList();
        return new ItemPassportResponse(respond(item),
                stoneRegistry.stonesOfItem(id), history);
    }

    // ---------- creation and maintenance ----------

    @Transactional
    public JewelleryItemResponse create(CreateItemRequest request) {
        ProductCatalog.ProductView product = productCatalog.requireProduct(request.productId());
        if (!product.active()) {
            throw new ValidationException("Product " + product.sku() + " is not active");
        }

        UUID metalId = request.metalId() != null ? request.metalId() : product.defaultMetalId();
        UUID purityId = request.purityId() != null ? request.purityId() : product.defaultPurityId();
        if (metalId == null || purityId == null) {
            throw new ValidationException(
                    "Metal and purity are required; the product has no defaults to fall back on");
        }
        // Confirms the purity exists and belongs to a known metal before we persist.
        metalRateProvider.fineness(purityId);

        OrganizationDirectory.LocationView location =
                organizationDirectory.requireLocation(request.locationId());
        if (!location.canHoldStock()) {
            throw new ValidationException("Location " + location.code() + " cannot hold stock");
        }

        String itemCode = StringUtils.hasText(request.itemCode())
                ? request.itemCode().trim().toUpperCase()
                : uniqueItemCode();
        if (itemRepository.existsByItemCodeIgnoreCase(itemCode)) {
            throw new ConflictException("Item code already exists: " + itemCode);
        }
        assertTagsAvailable(request.rfidTag(), request.qrCode(), request.barcode(), null);

        JewelleryItem item = new JewelleryItem();
        item.setItemCode(itemCode);
        item.setProductId(product.id());
        item.setDesignId(product.designId());
        item.setMetalId(metalId);
        item.setPurityId(purityId);
        item.setGrossWeight(MoneyUtils.weight(request.grossWeight()));
        item.setSizeId(request.sizeId());
        item.setRfidTag(emptyToNull(request.rfidTag()));
        item.setQrCode(emptyToNull(request.qrCode()));
        item.setBarcode(emptyToNull(request.barcode()));
        item.setHallmarkNumber(request.hallmarkNumber());
        item.setPurchaseCost(MoneyUtils.money(request.purchaseCost()));
        item.setMakingCost(MoneyUtils.money(request.makingCost()));
        item.setStoneCost(MoneyUtils.money(request.stoneCost()));
        item.setSupplierId(request.supplierId());
        item.setReceivedDate(request.receivedDate() == null ? LocalDate.now() : request.receivedDate());
        item.setCurrentLocationId(location.id());
        item.setCurrentBranchId(location.branchId());
        item.setStatus(ItemStatus.DRAFT);
        item.setNotes(request.notes());
        item.setStoneWeight(BigDecimal.ZERO);
        item.recalculateNetMetalWeight();

        JewelleryItem saved = itemRepository.save(item);
        applyStones(saved, request.stones());
        recalculateCost(saved);

        lifecycle.record(saved.getId(), LifecycleEventType.CREATED, null, ItemStatus.DRAFT,
                null, location.id(), "Product", product.sku(), "Item created");
        auditService.record("ITEM_CREATED", "JewelleryItem", saved.getId(), null,
                JewelleryItemResponse.from(saved), location.branchId());
        return respond(saved);
    }

    @Transactional
    public JewelleryItemResponse update(UUID id, UpdateItemRequest request) {
        JewelleryItem item = requireItemEntity(id);
        if (item.getStatus() == ItemStatus.SOLD || item.getStatus().isTerminal()) {
            throw new ConflictException(
                    "A " + item.getStatus() + " item cannot be edited");
        }
        JewelleryItemResponse before = JewelleryItemResponse.from(item);

        item.setGrossWeight(MoneyUtils.weight(request.grossWeight()));
        item.setSizeId(request.sizeId());
        item.setHallmarkNumber(request.hallmarkNumber());
        item.setPurchaseCost(MoneyUtils.money(request.purchaseCost()));
        item.setMakingCost(MoneyUtils.money(request.makingCost()));
        item.setStoneCost(MoneyUtils.money(request.stoneCost()));
        item.setNotes(request.notes());

        if (request.stones() != null) {
            applyStones(item, request.stones());
        } else {
            item.recalculateNetMetalWeight();
        }
        recalculateCost(item);

        JewelleryItemResponse after = JewelleryItemResponse.from(item);
        auditService.record("ITEM_UPDATED", "JewelleryItem", id, before, after,
                item.getCurrentBranchId());
        return respond(item);
    }

    @Transactional
    public JewelleryItemResponse tag(UUID id, TagItemRequest request) {
        JewelleryItem item = requireItemEntity(id);
        assertTagsAvailable(request.rfidTag(), request.qrCode(), request.barcode(), id);

        if (StringUtils.hasText(request.rfidTag())) {
            item.setRfidTag(request.rfidTag().trim());
        }
        if (StringUtils.hasText(request.qrCode())) {
            item.setQrCode(request.qrCode().trim());
        }
        if (StringUtils.hasText(request.barcode())) {
            item.setBarcode(request.barcode().trim());
        }

        lifecycle.record(id, LifecycleEventType.TAGGED, "Physical tags attached");
        auditService.record("ITEM_TAGGED", "JewelleryItem", id, null,
                JewelleryItemResponse.from(item), item.getCurrentBranchId());
        return respond(item);
    }

    /**
     * Puts the item in a storage bin, or takes it out of one.
     *
     * <p>The bin must belong to the item's current location. Without that
     * check an item could be recorded in a tray on the other side of the
     * country, which is worse than having no bin at all: a stock count would
     * report it missing from a vault nobody had reason to search.
     */
    @Transactional
    public JewelleryItemResponse assignBin(UUID id, AssignBinRequest request) {
        JewelleryItem item = requireItemEntity(id);
        UUID binId = request == null ? null : request.binId();

        if (binId == null) {
            item.setBinId(null);
            lifecycle.record(id, LifecycleEventType.LOCATION_CHANGED, "Removed from bin");
            auditService.record("ITEM_BIN_CLEARED", "JewelleryItem", id, null, null,
                    item.getCurrentBranchId());
            return respond(item);
        }

        BinDirectory.BinView bin = binDirectory.requireBin(binId);
        if (!bin.active()) {
            throw new ValidationException("Bin " + bin.code() + " is not active");
        }
        if (item.getCurrentLocationId() == null
                || !bin.locationId().equals(item.getCurrentLocationId())) {
            throw new ValidationException(
                    "Bin " + bin.code() + " belongs to a different location than the item");
        }

        item.setBinId(binId);
        lifecycle.record(id, LifecycleEventType.LOCATION_CHANGED,
                "Placed in bin " + bin.code());
        auditService.record("ITEM_BIN_ASSIGNED", "JewelleryItem", id, null,
                Map.of("binId", binId, "binCode", bin.code()), item.getCurrentBranchId());
        return respond(item);
    }

    @Transactional(readOnly = true)
    public List<ItemImageResponse> images(UUID id) {
        requireItemEntity(id);
        return imageRepository.findAllByItemIdOrderByDisplayOrderAscCreatedAtAsc(id).stream()
                .map(ItemImageResponse::from)
                .toList();
    }

    /**
     * Links an already-uploaded file to the item.
     *
     * <p>A new primary image demotes the previous one rather than failing: the
     * table allows only one per item, and asking a user to unset the old
     * picture before setting a new one is ceremony with no purpose.
     */
    @Transactional
    public ItemImageResponse addImage(UUID id, LinkImageRequest request) {
        JewelleryItem item = requireItemEntity(id);

        if (request.primaryImage()) {
            imageRepository.findAllByItemIdAndPrimaryImageTrue(id)
                    .forEach(existing -> existing.setPrimaryImage(false));
            imageRepository.flush();
        }

        ItemImage image = new ItemImage();
        image.setItem(item);
        image.setStorageKey(request.storageKey().trim());
        image.setFileName(request.fileName());
        image.setContentType(request.contentType());
        image.setSizeBytes(request.sizeBytes());
        // The first picture of a piece is its primary one; nobody should have
        // to say so explicitly.
        image.setPrimaryImage(request.primaryImage()
                || imageRepository.findAllByItemIdOrderByDisplayOrderAscCreatedAtAsc(id).isEmpty());
        image.setDisplayOrder(request.displayOrder());

        ItemImage saved = imageRepository.saveAndFlush(image);
        auditService.record("ITEM_IMAGE_ADDED", "JewelleryItem", id, null,
                Map.of("storageKey", saved.getStorageKey()), item.getCurrentBranchId());
        return ItemImageResponse.from(saved);
    }

    /**
     * Promotes an image to primary, demotes it, or moves it in the gallery.
     *
     * <p>Promotion demotes the previous primary for the same reason
     * {@link #addImage} does. Demoting the primary hands the role to the next
     * picture in order rather than leaving the item with none.
     */
    @Transactional
    public ItemImageResponse updateImage(UUID id, UUID imageId, UpdateImageRequest request) {
        JewelleryItem item = requireItemEntity(id);
        ItemImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image not found"));
        if (!image.getItem().getId().equals(id)) {
            throw new ValidationException("That image does not belong to this item");
        }
        if (request == null || (request.primaryImage() == null && request.displayOrder() == null)) {
            throw new ValidationException("Nothing to change: send primaryImage or displayOrder");
        }

        if (request.displayOrder() != null) {
            image.setDisplayOrder(request.displayOrder());
        }
        if (Boolean.TRUE.equals(request.primaryImage()) && !image.isPrimaryImage()) {
            imageRepository.findAllByItemIdAndPrimaryImageTrue(id)
                    .forEach(existing -> existing.setPrimaryImage(false));
            imageRepository.flush();
            image.setPrimaryImage(true);
        } else if (Boolean.FALSE.equals(request.primaryImage()) && image.isPrimaryImage()) {
            image.setPrimaryImage(false);
            imageRepository.flush();
            imageRepository.findAllByItemIdOrderByDisplayOrderAscCreatedAtAsc(id).stream()
                    .filter(other -> !other.getId().equals(imageId))
                    .findFirst()
                    .ifPresent(next -> next.setPrimaryImage(true));
        }

        ItemImage saved = imageRepository.saveAndFlush(image);
        auditService.record("ITEM_IMAGE_UPDATED", "JewelleryItem", id, null,
                Map.of("imageId", imageId, "primaryImage", saved.isPrimaryImage()),
                item.getCurrentBranchId());
        return ItemImageResponse.from(saved);
    }

    /**
     * Unlinks an image.
     *
     * <p>The stored object is deliberately left in place. Deleting it here
     * would destroy the binary while any other reference to the same key — a
     * repair condition photo, for instance — still pointed at it.
     */
    @Transactional
    public void removeImage(UUID id, UUID imageId) {
        JewelleryItem item = requireItemEntity(id);
        ItemImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image not found"));
        if (!image.getItem().getId().equals(id)) {
            throw new ValidationException("That image does not belong to this item");
        }
        boolean wasPrimary = image.isPrimaryImage();
        imageRepository.delete(image);
        imageRepository.flush();

        // Removing the primary picture must not leave the item with none, or
        // the passport header silently falls back to a placeholder.
        if (wasPrimary) {
            imageRepository.findAllByItemIdOrderByDisplayOrderAscCreatedAtAsc(id).stream()
                    .findFirst()
                    .ifPresent(next -> next.setPrimaryImage(true));
        }
        auditService.record("ITEM_IMAGE_REMOVED", "JewelleryItem", id, null, null,
                item.getCurrentBranchId());
    }

    /**
     * Passes quality check and releases the item into sellable stock.
     */
    @Transactional
    public JewelleryItemResponse approveForStock(UUID id) {
        JewelleryItem item = requireItemEntity(id);
        if (item.getStatus() != ItemStatus.DRAFT) {
            throw new ConflictException("Only DRAFT items can be released into stock");
        }
        if (!StringUtils.hasText(item.getBarcode()) && !StringUtils.hasText(item.getRfidTag())
                && !StringUtils.hasText(item.getQrCode())) {
            throw new ValidationException("Tag the item before releasing it into stock");
        }
        item.setQualityChecked(true);
        item.transitionTo(ItemStatus.AVAILABLE);

        lifecycle.record(id, LifecycleEventType.QUALITY_CHECKED, ItemStatus.DRAFT,
                ItemStatus.AVAILABLE, null, item.getCurrentLocationId(), null, null,
                "Quality checked and released into stock");
        auditService.record("ITEM_RELEASED_TO_STOCK", "JewelleryItem", id, null, null,
                item.getCurrentBranchId());
        return respond(item);
    }

    /** Manual status correction. Restricted and always audited. */
    @Transactional
    public JewelleryItemResponse changeStatus(UUID id, ChangeStatusRequest request) {
        JewelleryItem item = lockItem(id);
        ItemStatus from = item.getStatus();
        item.transitionTo(request.targetStatus());
        if (request.targetStatus() != ItemStatus.RESERVED) {
            item.clearReservation();
        }

        lifecycle.record(id, LifecycleEventType.STATUS_CHANGED, from, request.targetStatus(),
                null, null, "Manual", null, request.reason());
        auditService.record("ITEM_STATUS_CHANGED", "JewelleryItem", id,
                java.util.Map.of("status", from),
                java.util.Map.of("status", request.targetStatus(), "reason", request.reason()),
                item.getCurrentBranchId());
        return respond(item);
    }

    // ---------- reservations ----------

    @Transactional
    public JewelleryItemResponse reserve(ReserveItemRequest request) {
        int hours = request.holdHours() == null ? DEFAULT_HOLD_HOURS : request.holdHours();
        reserve(request.jewelleryItemId(), request.customerId(), hours);
        JewelleryItem item = requireItemEntity(request.jewelleryItemId());
        return respond(item);
    }

    @Override
    @Transactional
    public void reserve(UUID itemId, UUID customerId, int holdHours) {
        JewelleryItem item = lockItem(itemId);

        // An expired hold is treated as released so the item is not stuck.
        if (item.getStatus() == ItemStatus.RESERVED && !item.isReservationActive()) {
            item.transitionTo(ItemStatus.AVAILABLE);
            item.clearReservation();
        }
        if (item.getStatus() != ItemStatus.AVAILABLE) {
            throw new ConflictException("Item " + item.getItemCode() + " is " + item.getStatus()
                    + " and cannot be reserved");
        }

        item.transitionTo(ItemStatus.RESERVED);
        item.setReservedForCustomerId(customerId);
        item.setReservedUntil(Instant.now().plusSeconds(Math.max(holdHours, 1) * 3600L));
        item.setReservedBy(SecurityUtils.currentUsername().orElse("system"));

        lifecycle.record(itemId, LifecycleEventType.RESERVED, ItemStatus.AVAILABLE,
                ItemStatus.RESERVED, null, null, "Customer", customerId,
                "Reserved for " + holdHours + "h");
        auditService.record("ITEM_RESERVED", "JewelleryItem", itemId, null,
                java.util.Map.of("customerId", customerId), item.getCurrentBranchId());
    }

    @Override
    @Transactional
    public void releaseReservation(UUID itemId) {
        JewelleryItem item = lockItem(itemId);
        if (item.getStatus() != ItemStatus.RESERVED) {
            throw new ConflictException("Item " + item.getItemCode() + " is not reserved");
        }
        item.transitionTo(ItemStatus.AVAILABLE);
        item.clearReservation();

        lifecycle.record(itemId, LifecycleEventType.RESERVATION_RELEASED, ItemStatus.RESERVED,
                ItemStatus.AVAILABLE, null, null, null, null, "Reservation released");
        auditService.record("ITEM_RESERVATION_RELEASED", "JewelleryItem", itemId, null, null,
                item.getCurrentBranchId());
    }

    // ---------- cross-module operations ----------

    /**
     * Brings a piece into stock from procurement. Deliberately reuses the same
     * validation and lifecycle recording as a manually created item, so goods
     * receipts cannot bypass the rules.
     */
    @Override
    @Transactional
    public UUID intake(ItemIntake intake) {
        CreateItemRequest request = new CreateItemRequest(
                null,
                intake.productId(),
                intake.metalId(),
                intake.purityId(),
                intake.grossWeight(),
                null,
                intake.rfidTag(),
                null,
                intake.barcode(),
                intake.hallmarkNumber(),
                intake.purchaseCost(),
                intake.makingCost(),
                intake.stoneCost(),
                intake.supplierId(),
                LocalDate.now(),
                intake.locationId(),
                intake.notes(),
                null);

        JewelleryItemResponse created = create(request);

        // Stones are not itemised on a goods receipt, so the aggregate stone
        // weight from the delivery note is applied directly.
        if (intake.stoneWeight() != null && intake.stoneWeight().signum() > 0) {
            JewelleryItem item = requireItemEntity(created.id());
            item.setStoneWeight(MoneyUtils.weight(intake.stoneWeight()));
            item.recalculateNetMetalWeight();
            if (item.getNetMetalWeight().signum() <= 0) {
                throw new ValidationException(
                        "Stone weight cannot equal or exceed the gross weight of the item");
            }
        }

        lifecycle.record(created.id(), LifecycleEventType.CREATED, null, ItemStatus.DRAFT,
                null, intake.locationId(), intake.sourceType(), intake.sourceReference(),
                "Received into stock");
        return created.id();
    }

    @Override
    @Transactional(readOnly = true)
    public ItemView requireItem(UUID itemId) {
        return toView(requireItemEntity(itemId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemView> itemsAtLocation(UUID locationId) {
        // Only stock that should physically be there: items in transit have left.
        return itemRepository.search(null, null, null, null, locationId, null, null, null, null,
                        null, null, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(item -> item.getStatus().isInStock())
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public void markSold(UUID itemId, UUID customerId, UUID saleId, BigDecimal salePrice) {
        JewelleryItem item = lockItem(itemId);

        if (item.getStatus() == ItemStatus.RESERVED
                && item.getReservedForCustomerId() != null
                && !item.getReservedForCustomerId().equals(customerId)
                && item.isReservationActive()) {
            throw new ConflictException(
                    "Item " + item.getItemCode() + " is reserved for another customer");
        }
        if (!item.getStatus().isInStock()) {
            throw new ConflictException(
                    "Item " + item.getItemCode() + " is " + item.getStatus() + " and cannot be sold");
        }

        ItemStatus from = item.getStatus();
        item.transitionTo(ItemStatus.SOLD);
        item.clearReservation();
        item.setOwnerCustomerId(customerId);
        item.setSoldDate(LocalDate.now());
        if (salePrice != null) {
            item.setCurrentPrice(MoneyUtils.money(salePrice));
            item.setPriceCalculatedAt(Instant.now());
        }

        lifecycle.record(itemId, LifecycleEventType.SOLD, from, ItemStatus.SOLD, null, null,
                "Sale", saleId, "Sold to customer " + customerId);
        auditService.record("ITEM_SOLD", "JewelleryItem", itemId, java.util.Map.of("status", from),
                java.util.Map.of("saleId", String.valueOf(saleId), "customerId", String.valueOf(customerId)),
                item.getCurrentBranchId());
    }

    @Override
    @Transactional
    public void markReturned(UUID itemId, UUID saleId, UUID returnToLocationId, String reason) {
        JewelleryItem item = lockItem(itemId);
        if (item.getStatus() != ItemStatus.SOLD) {
            throw new ConflictException("Only a SOLD item can be returned");
        }
        OrganizationDirectory.LocationView location =
                organizationDirectory.requireLocation(returnToLocationId);
        if (!location.canHoldStock()) {
            throw new ValidationException("Return location cannot hold stock");
        }

        UUID previousLocation = item.getCurrentLocationId();
        item.transitionTo(ItemStatus.RETURNED);
        item.setOwnerCustomerId(null);
        item.setSoldDate(null);
        item.setCurrentLocationId(location.id());
        item.setCurrentBranchId(location.branchId());

        lifecycle.record(itemId, LifecycleEventType.RETURNED, ItemStatus.SOLD, ItemStatus.RETURNED,
                previousLocation, location.id(), "Sale", saleId, reason);
        auditService.record("ITEM_RETURNED", "JewelleryItem", itemId, null,
                java.util.Map.of("saleId", String.valueOf(saleId), "reason", String.valueOf(reason)),
                location.branchId());
    }

    // ---------- helpers ----------

    JewelleryItem requireItemEntity(UUID id) {
        return itemRepository.findById(id).orElseThrow(() -> NotFoundException.of("JewelleryItem", id));
    }

    /** The response the client sees: the item with its references labelled. */
    private JewelleryItemResponse respond(JewelleryItem item) {
        return JewelleryItemResponse.from(item, displayNames.resolve(List.of(item)));
    }

    JewelleryItem lockItem(UUID id) {
        return itemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("JewelleryItem", id));
    }

    private ItemView toView(JewelleryItem i) {
        return new ItemView(i.getId(), i.getItemCode(), i.getProductId(), i.getMetalId(),
                i.getPurityId(), i.getGrossWeight(), i.getNetMetalWeight(), i.getStoneWeight(),
                i.getTotalCarat(), i.getStoneCost(), i.getTotalCost(), i.getCurrentPrice(),
                i.getStatus().name(),
                i.getCurrentLocationId(), i.getCurrentBranchId(), i.getStatus().isInStock());
    }

    private void applyStones(JewelleryItem item, List<StoneRegistry.StoneSpec> stones) {
        StoneRegistry.StoneTotals totals = stoneRegistry.replaceStonesOf(item.getId(), stones);
        item.setStoneCount(totals.stoneCount());
        item.setStoneWeight(totals.totalWeightGrams());
        item.setTotalCarat(totals.totalCarat());
        if (totals.totalValue() != null && totals.totalValue().signum() > 0) {
            item.setStoneCost(totals.totalValue());
        }
        item.recalculateNetMetalWeight();
        if (item.getNetMetalWeight().signum() <= 0) {
            throw new ValidationException(
                    "Stone weight cannot equal or exceed the gross weight of the item");
        }
    }

    private void recalculateCost(JewelleryItem item) {
        BigDecimal total = MoneyUtils.nullSafe(item.getPurchaseCost())
                .add(MoneyUtils.nullSafe(item.getMakingCost()))
                .add(MoneyUtils.nullSafe(item.getStoneCost()));
        item.setTotalCost(MoneyUtils.money(total));
    }

    private String uniqueItemCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = CodeGenerator.reference("ITM");
            if (!itemRepository.existsByItemCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Could not allocate a unique item code; retry the request");
    }

    private void assertTagsAvailable(String rfid, String qr, String barcode, UUID selfId) {
        checkTag(rfid, itemRepository.findByRfidTag(rfid == null ? "" : rfid).map(JewelleryItem::getId),
                selfId, "RFID tag");
        checkTag(qr, itemRepository.findByQrCode(qr == null ? "" : qr).map(JewelleryItem::getId),
                selfId, "QR code");
        checkTag(barcode, itemRepository.findByBarcode(barcode == null ? "" : barcode)
                .map(JewelleryItem::getId), selfId, "Barcode");
    }

    private void checkTag(String value, java.util.Optional<UUID> ownerId, UUID selfId, String label) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (ownerId.isPresent() && !ownerId.get().equals(selfId)) {
            throw new ConflictException(label + " is already assigned to another item: " + value);
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
