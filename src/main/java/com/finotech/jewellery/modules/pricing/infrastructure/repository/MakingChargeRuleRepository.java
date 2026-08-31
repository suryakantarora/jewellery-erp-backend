package com.finotech.jewellery.modules.pricing.infrastructure.repository;

import com.finotech.jewellery.modules.pricing.domain.entity.MakingChargeRule;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MakingChargeRuleRepository extends JpaRepository<MakingChargeRule, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Every rule whose scope is compatible with the item being priced. A null
     * scope column matches anything; the caller then picks the most specific.
     */
    @Query("""
            select r from MakingChargeRule r
            where r.active = true
              and r.effectiveFrom <= :onDate
              and (r.effectiveTo is null or r.effectiveTo >= :onDate)
              and (r.productId is null or r.productId = :productId)
              and (r.productTypeId is null or r.productTypeId = :productTypeId)
              and (r.metalId is null or r.metalId = :metalId)
              and (r.purityId is null or r.purityId = :purityId)
              and (r.branchId is null or r.branchId = :branchId)
            """)
    List<MakingChargeRule> findCandidates(@Param("productId") UUID productId,
                                          @Param("productTypeId") UUID productTypeId,
                                          @Param("metalId") UUID metalId,
                                          @Param("purityId") UUID purityId,
                                          @Param("branchId") UUID branchId,
                                          @Param("onDate") LocalDate onDate);

    List<MakingChargeRule> findAllByOrderByPriorityDescCodeAsc();
}
