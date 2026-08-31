package com.finotech.jewellery.modules.procurement.infrastructure.repository;

import com.finotech.jewellery.modules.procurement.domain.entity.SupplierInvoice;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {

    boolean existsBySupplierIdAndInvoiceNumberIgnoreCase(UUID supplierId, String invoiceNumber);

    @Query("""
            select i from SupplierInvoice i
            where (:supplierId is null or i.supplierId = :supplierId)
              and (cast(:status as string) is null or i.status = :status)
            order by i.invoiceDate desc
            """)
    Page<SupplierInvoice> search(@Param("supplierId") UUID supplierId,
                                 @Param("status") String status,
                                 Pageable pageable);
}
