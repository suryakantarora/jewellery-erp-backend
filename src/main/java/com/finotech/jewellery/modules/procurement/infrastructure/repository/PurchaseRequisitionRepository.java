package com.finotech.jewellery.modules.procurement.infrastructure.repository;

import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseRequisition;
import com.finotech.jewellery.modules.procurement.domain.enums.RequisitionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<PurchaseRequisition> findWithLinesById(UUID id);

    @Query("""
            select r from PurchaseRequisition r
            where (:status is null or r.status = :status)
              and (:branchId is null or r.branchId = :branchId)
            """)
    Page<PurchaseRequisition> search(@Param("status") RequisitionStatus status,
                                     @Param("branchId") UUID branchId,
                                     Pageable pageable);
}
