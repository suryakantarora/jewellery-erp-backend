package com.finotech.jewellery.modules.inventory.infrastructure.repository;

import com.finotech.jewellery.modules.inventory.domain.entity.InventoryMovement;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<InventoryMovement> findWithLinesById(UUID id);

    Optional<InventoryMovement> findByReferenceNumberIgnoreCase(String referenceNumber);

    boolean existsByExternalReference(String externalReference);

    Optional<InventoryMovement> findByExternalReference(String externalReference);

    @Query("""
            select m from InventoryMovement m
            where (:status is null or m.status = :status)
              and (:movementType is null or m.movementType = :movementType)
              and (:fromLocationId is null or m.fromLocationId = :fromLocationId)
              and (:toLocationId is null or m.toLocationId = :toLocationId)
              and (cast(:from as timestamp) is null or m.createdAt >= :from)
              and (cast(:to as timestamp) is null or m.createdAt <= :to)
            """)
    Page<InventoryMovement> search(@Param("status") MovementStatus status,
                                   @Param("movementType") MovementType movementType,
                                   @Param("fromLocationId") UUID fromLocationId,
                                   @Param("toLocationId") UUID toLocationId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   Pageable pageable);

    /**
     * Transfers awaiting approval that touch one of the given branches, oldest
     * first, paged so an approval queue never misses the oldest of a busy branch
     * behind a platform-wide cap. A null or empty collection means every
     * branch (a super administrator). {@code companyId} is the tenant boundary,
     * applied the same way {@link JewelleryItemRepository#search} applies it;
     * null means every company.
     */
    default List<InventoryMovement> findPendingApprovalInBranches(UUID companyId,
                                                                  Collection<UUID> branchIds,
                                                                  Pageable pageable) {
        return branchIds == null || branchIds.isEmpty()
                ? findPendingApprovalAnyBranch(companyId, pageable)
                : findPendingApprovalTouchingBranches(companyId, branchIds, pageable);
    }

    @Query("""
            select m from InventoryMovement m
            where m.status = com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus.PENDING_APPROVAL
              and (m.fromBranchId in :branchIds or m.toBranchId in :branchIds)
              and (:companyId is null
                   or m.toBranchId in (select b.id from com.finotech.jewellery.modules.organization.domain.entity.Branch b
                                        where b.company.id = :companyId)
                   or m.fromBranchId in (select b.id from com.finotech.jewellery.modules.organization.domain.entity.Branch b
                                          where b.company.id = :companyId))
            order by m.createdAt asc
            """)
    List<InventoryMovement> findPendingApprovalTouchingBranches(@Param("companyId") UUID companyId,
                                                                @Param("branchIds") Collection<UUID> branchIds,
                                                                Pageable pageable);

    @Query("""
            select m from InventoryMovement m
            where m.status = com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus.PENDING_APPROVAL
              and (:companyId is null
                   or m.toBranchId in (select b.id from com.finotech.jewellery.modules.organization.domain.entity.Branch b
                                        where b.company.id = :companyId)
                   or m.fromBranchId in (select b.id from com.finotech.jewellery.modules.organization.domain.entity.Branch b
                                          where b.company.id = :companyId))
            order by m.createdAt asc
            """)
    List<InventoryMovement> findPendingApprovalAnyBranch(@Param("companyId") UUID companyId,
                                                         Pageable pageable);
}
