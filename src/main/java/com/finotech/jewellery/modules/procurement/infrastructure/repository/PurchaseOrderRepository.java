package com.finotech.jewellery.modules.procurement.infrastructure.repository;

import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrder;
import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<PurchaseOrder> findWithLinesById(UUID id);

    Optional<PurchaseOrder> findByExternalReference(String externalReference);

    @Query("""
            select o from PurchaseOrder o
            where (:status is null or o.status = :status)
              and (:supplierId is null or o.supplierId = :supplierId)
              and (:branchId is null or o.branchId = :branchId)
            """)
    Page<PurchaseOrder> search(@Param("status") PurchaseOrderStatus status,
                               @Param("supplierId") UUID supplierId,
                               @Param("branchId") UUID branchId,
                               Pageable pageable);
}
