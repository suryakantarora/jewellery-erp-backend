package com.finotech.jewellery.modules.procurement.infrastructure.repository;

import com.finotech.jewellery.modules.procurement.domain.entity.GoodsReceipt;
import com.finotech.jewellery.modules.procurement.domain.enums.GoodsReceiptStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<GoodsReceipt> findWithLinesById(UUID id);

    Optional<GoodsReceipt> findByExternalReference(String externalReference);

    @Query("""
            select g from GoodsReceipt g
            where (:status is null or g.status = :status)
              and (:purchaseOrderId is null or g.purchaseOrderId = :purchaseOrderId)
              and (:branchId is null or g.branchId = :branchId)
            """)
    Page<GoodsReceipt> search(@Param("status") GoodsReceiptStatus status,
                              @Param("purchaseOrderId") UUID purchaseOrderId,
                              @Param("branchId") UUID branchId,
                              Pageable pageable);
}
