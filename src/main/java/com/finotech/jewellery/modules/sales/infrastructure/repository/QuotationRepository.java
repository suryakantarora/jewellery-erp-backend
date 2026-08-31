package com.finotech.jewellery.modules.sales.infrastructure.repository;

import com.finotech.jewellery.modules.sales.domain.entity.Quotation;
import com.finotech.jewellery.modules.sales.domain.enums.QuotationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<Quotation> findWithLinesById(UUID id);

    @Query("""
            select q from Quotation q
            where (:status is null or q.status = :status)
              and (:customerId is null or q.customerId = :customerId)
              and (:branchId is null or q.branchId = :branchId)
            """)
    Page<Quotation> search(@Param("status") QuotationStatus status,
                           @Param("customerId") UUID customerId,
                           @Param("branchId") UUID branchId,
                           Pageable pageable);
}
