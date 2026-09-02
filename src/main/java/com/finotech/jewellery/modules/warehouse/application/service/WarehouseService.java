package com.finotech.jewellery.modules.warehouse.application.service;

import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.modules.warehouse.api.request.WarehouseRequests;
import com.finotech.jewellery.modules.warehouse.api.response.WarehouseResponses.BinResponse;
import com.finotech.jewellery.modules.warehouse.api.response.WarehouseResponses.StockCountResponse;
import com.finotech.jewellery.modules.warehouse.domain.entity.StockCount;
import com.finotech.jewellery.modules.warehouse.domain.entity.StockCountLine;
import com.finotech.jewellery.modules.warehouse.domain.entity.StorageBin;
import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
import com.finotech.jewellery.modules.warehouse.infrastructure.repository.StockCountRepository;
import com.finotech.jewellery.modules.warehouse.infrastructure.repository.StorageBinRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouse and vault: bin-level storage and physical stock verification
 * (section 19).
 *
 * <p>A count snapshots what the system expects, records what was found, and
 * reports the difference. It deliberately does <em>not</em> adjust stock on its
 * own — a missing item in a vault is an incident to investigate, not a number
 * to quietly correct. Corrections are made through the inventory adjustment
 * endpoint, which requires its own permission and is audited separately.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final Set<StockCountStatus> OPEN_STATUSES =
            Set.of(StockCountStatus.IN_PROGRESS, StockCountStatus.PENDING_REVIEW,
                    StockCountStatus.APPROVED);

    private final StorageBinRepository binRepository;
    private final StockCountRepository stockCountRepository;
    private final OrganizationDirectory organizationDirectory;
    private final InventoryOperations inventory;
    private final AuditService auditService;

    // ---------- storage bins ----------

    @Transactional(readOnly = true)
    public List<BinResponse> listBins(UUID locationId) {
        return binRepository.findAllByLocationIdOrderByCodeAsc(locationId).stream()
                .map(BinResponse::from).toList();
    }

    @Transactional
    public BinResponse createBin(WarehouseRequests.BinRequest request) {
        if (binRepository.existsByLocationIdAndCodeIgnoreCase(request.locationId(),
                request.code())) {
            throw new ConflictException(
                    "Bin code already exists at this location: " + request.code());
        }
        OrganizationDirectory.LocationView location =
                organizationDirectory.requireLocation(request.locationId());
        if (!location.canHoldStock()) {
            throw new ValidationException("Location " + location.code() + " cannot hold stock");
        }

        StorageBin bin = new StorageBin();
        bin.setLocationId(location.id());
        bin.setCode(request.code().trim().toUpperCase());
        bin.setName(request.name().trim());
        bin.setBinType(request.binType());
        bin.setCapacity(request.capacity());
        bin.setDescription(request.description());

        if (request.parentId() != null) {
            StorageBin parent = binRepository.findById(request.parentId())
                    .orElseThrow(() -> NotFoundException.of("StorageBin", request.parentId()));
            if (!parent.getLocationId().equals(location.id())) {
                throw new ValidationException("Parent bin belongs to a different location");
            }
            bin.setParent(parent);
        }

        StorageBin saved = binRepository.save(bin);
        auditService.record("STORAGE_BIN_CREATED", "StorageBin", saved.getId(), null,
                BinResponse.from(saved), location.branchId());
        return BinResponse.from(saved);
    }

    @Transactional
    public BinResponse deactivateBin(UUID id) {
        StorageBin bin = binRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("StorageBin", id));
        if (binRepository.existsByParentId(id)) {
            throw new ValidationException("Deactivate or move the child bins first");
        }
        bin.setActive(false);
        return BinResponse.from(bin);
    }

    // ---------- stock verification ----------

    @Transactional(readOnly = true)
    public PageResponse<StockCountResponse> searchCounts(StockCountStatus status, UUID locationId,
                                                         UUID branchId, Pageable pageable) {
        return PageResponse.of(stockCountRepository.search(status, locationId, branchId, pageable),
                StockCountResponse::from);
    }

    @Transactional(readOnly = true)
    public StockCountResponse getCount(UUID id) {
        return StockCountResponse.withLines(requireCount(id));
    }

    /**
     * Opens a count and snapshots the expected stock.
     *
     * <p>Only one count may be open per location: two simultaneous counts would
     * each see the other's changes and neither would be trustworthy.
     */
    @Transactional
    public StockCountResponse startCount(WarehouseRequests.StartCountRequest request) {
        OrganizationDirectory.LocationView location =
                organizationDirectory.requireLocation(request.locationId());
        SecurityUtils.requireBranchAccess(location.branchId());

        if (stockCountRepository.existsByLocationIdAndStatusIn(location.id(), OPEN_STATUSES)) {
            throw new ConflictException(
                    "A stock count is already open for location " + location.code());
        }

        StockCount count = new StockCount();
        count.setReferenceNumber(CodeGenerator.reference("SC"));
        count.setLocationId(location.id());
        count.setBranchId(location.branchId());
        count.setCountDate(LocalDate.now());
        count.setDualAuthorization(location.dualAuthorization());
        count.setNotes(request.notes());
        count.setStatus(StockCountStatus.IN_PROGRESS);

        List<InventoryOperations.ItemView> expected = inventory.itemsAtLocation(location.id());
        for (InventoryOperations.ItemView item : expected) {
            StockCountLine line = new StockCountLine();
            line.setJewelleryItemId(item.id());
            line.setItemCode(item.itemCode());
            line.setExpected(true);
            count.addLine(line);
        }
        count.setExpectedCount(expected.size());

        StockCount saved = stockCountRepository.save(count);
        auditService.record("STOCK_COUNT_STARTED", "StockCount", saved.getId(), null,
                Map.of("location", location.code(), "expectedCount", expected.size()),
                location.branchId());
        return StockCountResponse.withLines(saved);
    }

    /**
     * Records what was physically found and computes the variance.
     *
     * <p>Items found that were not expected are added as extra lines, so a
     * misplaced piece shows up rather than being silently ignored.
     */
    @Transactional
    public StockCountResponse submitCount(UUID id, WarehouseRequests.SubmitCountRequest request) {
        StockCount count = requireCount(id);
        if (count.getStatus() != StockCountStatus.IN_PROGRESS) {
            throw new ConflictException("Count " + count.getReferenceNumber() + " is "
                    + count.getStatus() + " and no longer accepts results");
        }

        Set<UUID> found = new HashSet<>(request.foundItemIds());
        int missing = 0;

        for (StockCountLine line : count.getLines()) {
            boolean present = found.remove(line.getJewelleryItemId());
            line.setCounted(present);
            if (line.isMissing()) {
                missing++;
                line.setVarianceNote("Expected at this location but not found");
            }
        }

        // Whatever is left in `found` was not on the expected sheet.
        for (UUID unexpectedId : found) {
            StockCountLine line = new StockCountLine();
            line.setJewelleryItemId(unexpectedId);
            line.setExpected(false);
            line.setCounted(true);
            line.setVarianceNote("Found here but not expected at this location");
            try {
                line.setItemCode(inventory.requireItem(unexpectedId).itemCode());
            } catch (RuntimeException ex) {
                line.setItemCode("UNKNOWN");
                line.setVarianceNote("Scanned item is not a known jewellery item");
            }
            count.addLine(line);
        }

        count.setCountedCount((int) count.getLines().stream().filter(StockCountLine::isCounted).count());
        count.setMissingCount(missing);
        count.setUnexpectedCount(found.size());
        count.setCountedBy(SecurityUtils.currentUsername().orElse("system"));
        count.setCountedAt(Instant.now());
        count.setStatus(StockCountStatus.PENDING_REVIEW);
        if (request.notes() != null) {
            count.setNotes(request.notes());
        }

        auditService.record("STOCK_COUNT_SUBMITTED", "StockCount", id, null,
                Map.of("expected", count.getExpectedCount(), "counted", count.getCountedCount(),
                        "missing", count.getMissingCount(),
                        "unexpected", count.getUnexpectedCount()),
                count.getBranchId());
        return StockCountResponse.withLines(count);
    }

    /**
     * Reviews the result. A vault, or any count with a variance, needs a second
     * approver before it can be closed.
     */
    @Transactional
    public StockCountResponse approveCount(UUID id) {
        StockCount count = requireCount(id);
        if (count.getStatus() != StockCountStatus.PENDING_REVIEW
                && count.getStatus() != StockCountStatus.APPROVED) {
            throw new ConflictException("Count " + count.getReferenceNumber()
                    + " is not awaiting review");
        }
        String approver = SecurityUtils.currentUsername().orElse("system");

        if (approver.equals(count.getCountedBy())) {
            throw new ValidationException(
                    "A count must be reviewed by someone other than the person who counted it");
        }

        boolean needsTwo = count.isDualAuthorization() || count.hasVariance();
        if (count.getApprovedBy() == null) {
            count.setApprovedBy(approver);
            count.setApprovedAt(Instant.now());
            count.setStatus(StockCountStatus.APPROVED);
            if (!needsTwo) {
                close(count);
            }
        } else {
            if (approver.equals(count.getApprovedBy())) {
                throw new ConflictException("This count needs a second, different approver");
            }
            count.setSecondApprovedBy(approver);
            count.setSecondApprovedAt(Instant.now());
            close(count);
        }

        auditService.record("STOCK_COUNT_APPROVED", "StockCount", id, null,
                Map.of("approvedBy", approver, "status", count.getStatus(),
                        "hasVariance", count.hasVariance()),
                count.getBranchId());
        return StockCountResponse.withLines(count);
    }

    @Transactional
    public StockCountResponse cancelCount(UUID id, String reason) {
        StockCount count = requireCount(id);
        if (count.getStatus() == StockCountStatus.CLOSED) {
            throw new ConflictException("A closed count cannot be cancelled");
        }
        count.setStatus(StockCountStatus.CANCELLED);
        count.setNotes(reason);
        auditService.record("STOCK_COUNT_CANCELLED", "StockCount", id, null,
                Map.of("reason", String.valueOf(reason)), count.getBranchId());
        return StockCountResponse.withLines(count);
    }

    // ---------- helpers ----------

    private void close(StockCount count) {
        count.setStatus(StockCountStatus.CLOSED);
        count.setClosedAt(Instant.now());
    }

    private StockCount requireCount(UUID id) {
        return stockCountRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("StockCount", id));
    }
}
