package com.finotech.jewellery.modules.inventory.infrastructure.repository;

import com.finotech.jewellery.modules.inventory.domain.entity.InventoryMovement;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementStatus;
import com.finotech.jewellery.modules.inventory.domain.enums.MovementType;
import java.time.Instant;
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
}
