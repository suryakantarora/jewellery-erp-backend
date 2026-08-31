package com.finotech.jewellery.modules.metal.infrastructure.repository;

import com.finotech.jewellery.modules.metal.domain.entity.MetalRate;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetalRateRepository extends JpaRepository<MetalRate, UUID> {

    /**
     * The rate in force on a date: the most recent publication on or before it.
     * A branch-specific rate wins over the company-wide one.
     */
    @Query("""
            select r from MetalRate r
            where r.metal.id = :metalId
              and r.purity.id = :purityId
              and r.rateType = :rateType
              and r.effectiveDate <= :onDate
              and (r.branchId = :branchId or r.branchId is null)
            order by r.effectiveDate desc,
                     case when r.branchId is not null then 0 else 1 end,
                     r.publishedAt desc
            """)
    Page<MetalRate> findEffectiveRates(@Param("metalId") UUID metalId,
                                       @Param("purityId") UUID purityId,
                                       @Param("rateType") RateType rateType,
                                       @Param("onDate") LocalDate onDate,
                                       @Param("branchId") UUID branchId,
                                       Pageable pageable);

    Optional<MetalRate> findFirstByMetalIdAndPurityIdAndRateTypeAndEffectiveDateAndBranchId(
            UUID metalId, UUID purityId, RateType rateType, LocalDate effectiveDate, UUID branchId);

    @Query("""
            select r from MetalRate r
            where (:metalId is null or r.metal.id = :metalId)
              and (:purityId is null or r.purity.id = :purityId)
              and (:rateType is null or r.rateType = :rateType)
              and (cast(:from as date) is null or r.effectiveDate >= :from)
              and (cast(:to as date) is null or r.effectiveDate <= :to)
            order by r.effectiveDate desc, r.publishedAt desc
            """)
    Page<MetalRate> search(@Param("metalId") UUID metalId,
                           @Param("purityId") UUID purityId,
                           @Param("rateType") RateType rateType,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           Pageable pageable);
}
