package com.finotech.jewellery.modules.pricing.infrastructure.repository;

import com.finotech.jewellery.modules.pricing.domain.entity.DiscountPolicy;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountPolicyRepository extends JpaRepository<DiscountPolicy, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    /** Branch-specific policy first, then the company-wide fallback. */
    @Query("""
            select p from DiscountPolicy p
            where p.active = true
              and p.effectiveFrom <= :onDate
              and (p.effectiveTo is null or p.effectiveTo >= :onDate)
              and (p.branchId = :branchId or p.branchId is null)
            order by case when p.branchId is not null then 0 else 1 end
            """)
    List<DiscountPolicy> findApplicable(@Param("branchId") UUID branchId,
                                        @Param("onDate") LocalDate onDate);

    List<DiscountPolicy> findAllByOrderByCodeAsc();
}
