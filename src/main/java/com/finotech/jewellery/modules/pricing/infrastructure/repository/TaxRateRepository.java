package com.finotech.jewellery.modules.pricing.infrastructure.repository;

import com.finotech.jewellery.modules.pricing.domain.entity.TaxRate;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    @Query("""
            select t from TaxRate t
            where t.active = true
              and t.effectiveFrom <= :onDate
              and (t.effectiveTo is null or t.effectiveTo >= :onDate)
              and (t.branchId is null or t.branchId = :branchId)
              and (t.productTypeId is null or t.productTypeId = :productTypeId)
            order by t.code
            """)
    List<TaxRate> findApplicable(@Param("branchId") UUID branchId,
                                 @Param("productTypeId") UUID productTypeId,
                                 @Param("onDate") LocalDate onDate);

    List<TaxRate> findAllByOrderByCodeAsc();
}
