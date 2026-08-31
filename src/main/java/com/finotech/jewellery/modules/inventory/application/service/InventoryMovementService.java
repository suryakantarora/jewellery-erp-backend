package com.finotech.jewellery.modules.inventory.application.service;

import com.finotech.jewellery.modules.inventory.api.request.CreateMovementRequest;
import com.finotech.jewellery.modules.inventory.api.request.ReceiveMovementRequest;
import com.finotech.jewellery.modules.inventory.api.request.RejectMovementRequest;
import com.finotech.jewellery.modules.inventory.api.response.MovementResponse;
import com.finotech.jewellery.modules.inventory.domain.entity.InventoryMovement;
import com.finotech.jewellery.modules.inventory.domain.entity.InventoryMovementLine;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.LifecycleEventType;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.InventoryMovementRepository;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.JewelleryItemRepository;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Movement of serialized items between locations.
 *
 * <p>The workflow is deliberately two-sided: items leave the source at dispatch
 * and become {@code IN_TRANSIT}, and only arrive at the destination when the
 * receiving branch confirms. Nothing is ever in two places at once, and goods
 * in transit are visible as such.
 *
 * <p>Items are locked with {@code SELECT … FOR UPDATE} while a movement is
 * raised or dispatched, so a transfer and a sale cannot both claim an item.
 */
@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private final InventoryMovementRepository movementRepository;
    private final JewelleryItemRepository itemRepository;
    private final OrganizationDirectory organizationDirectory;
    private final ItemLifecycleRecorder lifecycle;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> search(MovementStatus status, MovementType movementType,
                                                 UUID fromLocationId, UUID toLocationId,
                                                 Instant from, Instant to, Pageable pageable) {
        return PageResponse.of(movementRepository.search(status, movementType, fromLocationId,
                toLocationId, from, to, pageable), MovementResponse::from);
    }

    @Transactional(readOnly = true)
    public MovementResponse get(UUID id) {
        return MovementResponse.from(requireMovement(id));
    }

    /**
     * Raises a movement.
     *
     * @param idempotencyKey optional client key; replaying the same key returns
     *                       the movement already created instead of a duplicate
     */
    @Transactional
    public MovementResponse create(CreateMovementRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = movementRepository.findByExternalReference(idempotencyKey);
            if (existing.isPresent()) {
                return MovementResponse.from(existing.get());
            }
        }

        OrganizationDirectory.LocationView to =
                organizationDirectory.requireLocation(request.toLocationId());
        if (!to.canHoldStock()) {
            throw new ValidationException("Destination " + to.code() + " cannot hold stock");
        }
        OrganizationDirectory.LocationView from = request.fromLocationId() == null ? null
                : organizationDirectory.requireLocation(request.fromLocationId());
        if (from != null && from.id().equals(to.id())) {
            throw new ValidationException("Source and destination must differ");
        }
        if (request.movementType() != MovementType.GOODS_RECEIPT && from == null) {
            throw new ValidationException("A source location is required for " + request.movementType());
        }

        InventoryMovement movement = new InventoryMovement();
        movement.setReferenceNumber(CodeGenerator.reference("MOV"));
        movement.setMovementType(request.movementType());
        movement.setFromLocationId(from == null ? null : from.id());
        movement.setFromBranchId(from == null ? null : from.branchId());
        movement.setToLocationId(to.id());
        movement.setToBranchId(to.branchId());
        movement.setNotes(request.notes());
        movement.setExternalReference(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);

        // Cross-branch moves and vault movements always need sign-off.
        boolean crossBranch = from != null && !from.branchId().equals(to.branchId());
        boolean vaultInvolved = (from != null && from.dualAuthorization()) || to.dualAuthorization();
        movement.setRequiresApproval(crossBranch || vaultInvolved);
        movement.setStatus(movement.isRequiresApproval()
                ? MovementStatus.PENDING_APPROVAL : MovementStatus.APPROVED);

        List<UUID> itemIds = request.itemIds().stream().distinct().toList();
        for (JewelleryItem item : itemRepository.findAllByIdForUpdate(itemIds)) {
            assertMovable(item, movement);
            InventoryMovementLine line = new InventoryMovementLine();
            line.setJewelleryItemId(item.getId());
            line.setItemCode(item.getItemCode());
            line.setDispatchedWeight(item.getGrossWeight());
            movement.addLine(line);
        }
        if (movement.getLines().size() != itemIds.size()) {
            throw new NotFoundException("One or more items in the request do not exist");
        }

        InventoryMovement saved = movementRepository.save(movement);
        auditService.record("MOVEMENT_CREATED", "InventoryMovement", saved.getId(), null,
                MovementResponse.from(saved), to.branchId());
        return MovementResponse.from(saved);
    }

    @Transactional
    public MovementResponse approve(UUID id) {
        InventoryMovement movement = requireMovement(id);
        if (movement.getStatus() != MovementStatus.PENDING_APPROVAL) {
            throw new ConflictException("Movement " + movement.getReferenceNumber()
                    + " is " + movement.getStatus() + " and cannot be approved");
        }
        String approver = SecurityUtils.currentUsername().orElse("system");

        boolean dualRequired = isDualAuthorizationRequired(movement);
        if (movement.getApprovedBy() == null) {
            movement.setApprovedBy(approver);
            movement.setApprovedAt(Instant.now());
            if (!dualRequired) {
                movement.setStatus(MovementStatus.APPROVED);
            }
        } else {
            // Second approver on a dual-authorization vault movement.
            if (movement.getApprovedBy().equals(approver)) {
                throw new ConflictException(
                        "This movement needs a second, different approver");
            }
            movement.setSecondApprovedBy(approver);
            movement.setSecondApprovedAt(Instant.now());
            movement.setStatus(MovementStatus.APPROVED);
        }

        auditService.record("MOVEMENT_APPROVED", "InventoryMovement", id, null,
                Map.of("approvedBy", approver, "status", movement.getStatus()),
                movement.getToBranchId());
        return MovementResponse.from(movement);
    }

    @Transactional
    public MovementResponse reject(UUID id, RejectMovementRequest request) {
        InventoryMovement movement = requireMovement(id);
        if (movement.getStatus() != MovementStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only a pending movement can be rejected");
        }
        movement.setStatus(MovementStatus.REJECTED);
        movement.setRejectionReason(request.reason());
        auditService.record("MOVEMENT_REJECTED", "InventoryMovement", id, null,
                Map.of("reason", request.reason()), movement.getToBranchId());
        return MovementResponse.from(movement);
    }

    /**
     * Releases the items from the source location; they become IN_TRANSIT.
     */
    @Transactional
    public MovementResponse dispatch(UUID id) {
        InventoryMovement movement = requireMovement(id);
        if (movement.getStatus() != MovementStatus.APPROVED) {
            throw new ConflictException("Movement " + movement.getReferenceNumber()
                    + " must be approved before dispatch");
        }

        for (JewelleryItem item : lockLineItems(movement)) {
            assertMovable(item, movement);
            ItemStatus from = item.getStatus();
            UUID previousLocation = item.getCurrentLocationId();
            item.transitionTo(ItemStatus.IN_TRANSIT);
            item.clearReservation();

            lifecycle.record(item.getId(), LifecycleEventType.STATUS_CHANGED, from,
                    ItemStatus.IN_TRANSIT, previousLocation, movement.getToLocationId(),
                    "InventoryMovement", movement.getReferenceNumber(), "Dispatched");
        }

        movement.setStatus(MovementStatus.DISPATCHED);
        movement.setDispatchedBy(SecurityUtils.currentUsername().orElse("system"));
        movement.setDispatchedAt(Instant.now());

        auditService.record("MOVEMENT_DISPATCHED", "InventoryMovement", id, null,
                Map.of("items", movement.getLines().size()), movement.getFromBranchId());
        return MovementResponse.from(movement);
    }

    /**
     * Confirms arrival: the items land at the destination and return to stock.
     */
    @Transactional
    public MovementResponse receive(UUID id, ReceiveMovementRequest request) {
        InventoryMovement movement = requireMovement(id);
        if (movement.getStatus() != MovementStatus.DISPATCHED) {
            throw new ConflictException("Only a dispatched movement can be received");
        }
        OrganizationDirectory.LocationView destination =
                organizationDirectory.requireLocation(movement.getToLocationId());

        Map<UUID, ReceiveMovementRequest.ReceivedLine> reported = new HashMap<>();
        if (request != null && request.lines() != null) {
            request.lines().forEach(l -> reported.put(l.jewelleryItemId(), l));
        }

        for (JewelleryItem item : lockLineItems(movement)) {
            if (item.getStatus() != ItemStatus.IN_TRANSIT) {
                throw new ConflictException("Item " + item.getItemCode()
                        + " is " + item.getStatus() + ", expected IN_TRANSIT");
            }
            UUID previousLocation = item.getCurrentLocationId();
            item.transitionTo(ItemStatus.AVAILABLE);
            item.setCurrentLocationId(destination.id());
            item.setCurrentBranchId(destination.branchId());

            InventoryMovementLine line = lineFor(movement, item.getId());
            line.setReceived(true);
            ReceiveMovementRequest.ReceivedLine reportedLine = reported.get(item.getId());
            if (reportedLine != null) {
                line.setReceivedWeight(reportedLine.receivedWeight());
                line.setDiscrepancyNote(reportedLine.discrepancyNote());
            } else {
                line.setReceivedWeight(line.getDispatchedWeight());
            }

            lifecycle.record(item.getId(), LifecycleEventType.LOCATION_CHANGED,
                    ItemStatus.IN_TRANSIT, ItemStatus.AVAILABLE, previousLocation, destination.id(),
                    "InventoryMovement", movement.getReferenceNumber(), "Received");
        }

        movement.setStatus(MovementStatus.COMPLETED);
        movement.setReceivedBy(SecurityUtils.currentUsername().orElse("system"));
        movement.setCompletedAt(Instant.now());
        if (request != null && StringUtils.hasText(request.notes())) {
            movement.setNotes(request.notes());
        }

        auditService.record("MOVEMENT_COMPLETED", "InventoryMovement", id, null,
                Map.of("items", movement.getLines().size()), destination.branchId());
        events.publish(new DomainEvents.ItemTransferred(movement.getId(),
                movement.getReferenceNumber(), destination.branchId(), destination.id(),
                movement.getLines().size()));
        return MovementResponse.from(movement);
    }

    @Transactional
    public MovementResponse cancel(UUID id) {
        InventoryMovement movement = requireMovement(id);
        if (movement.getStatus() == MovementStatus.COMPLETED
                || movement.getStatus() == MovementStatus.DISPATCHED) {
            throw new ConflictException(
                    "A dispatched or completed movement cannot be cancelled; receive it instead");
        }
        movement.setStatus(MovementStatus.CANCELLED);
        auditService.record("MOVEMENT_CANCELLED", "InventoryMovement", id, null, null,
                movement.getToBranchId());
        return MovementResponse.from(movement);
    }

    // ---------- helpers ----------

    private InventoryMovement requireMovement(UUID id) {
        return movementRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("InventoryMovement", id));
    }

    private List<JewelleryItem> lockLineItems(InventoryMovement movement) {
        List<UUID> ids = movement.getLines().stream()
                .map(InventoryMovementLine::getJewelleryItemId).toList();
        return itemRepository.findAllByIdForUpdate(ids);
    }

    private InventoryMovementLine lineFor(InventoryMovement movement, UUID itemId) {
        return movement.getLines().stream()
                .filter(l -> l.getJewelleryItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Item " + itemId + " is not on this movement"));
    }

    private boolean isDualAuthorizationRequired(InventoryMovement movement) {
        boolean fromVault = movement.getFromLocationId() != null
                && organizationDirectory.requireLocation(movement.getFromLocationId()).dualAuthorization();
        boolean toVault = organizationDirectory.requireLocation(movement.getToLocationId())
                .dualAuthorization();
        return fromVault || toVault;
    }

    /**
     * An item may only move when it is physically in stock at the source. This
     * is what prevents transferring something that is sold or under repair.
     */
    private void assertMovable(JewelleryItem item, InventoryMovement movement) {
        if (movement.getMovementType() == MovementType.GOODS_RECEIPT) {
            return;
        }
        if (!item.getStatus().isInStock() && item.getStatus() != ItemStatus.IN_TRANSIT) {
            throw new ConflictException("Item " + item.getItemCode() + " is " + item.getStatus()
                    + " and cannot be moved");
        }
        if (movement.getFromLocationId() != null
                && item.getCurrentLocationId() != null
                && !movement.getFromLocationId().equals(item.getCurrentLocationId())
                && item.getStatus() != ItemStatus.IN_TRANSIT) {
            throw new ValidationException("Item " + item.getItemCode()
                    + " is not at the source location of this movement");
        }
    }
}
