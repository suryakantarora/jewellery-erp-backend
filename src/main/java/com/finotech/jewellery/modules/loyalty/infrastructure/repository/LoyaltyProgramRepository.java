package com.finotech.jewellery.modules.loyalty.infrastructure.repository;

import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyProgram;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = "tiers")
    Optional<LoyaltyProgram> findWithTiersById(UUID id);

    /** The program in force today; the newest one wins if several overlap. */
    @Query("""
            select p from LoyaltyProgram p
            where p.active = true
              and p.effectiveFrom <= :onDate
              and (p.effectiveTo is null or p.effectiveTo >= :onDate)
            order by p.effectiveFrom desc
            """)
    List<LoyaltyProgram> findActive(@Param("onDate") LocalDate onDate, Pageable pageable);

    @EntityGraph(attributePaths = "tiers")
    List<LoyaltyProgram> findAllByOrderByCodeAsc();
}
