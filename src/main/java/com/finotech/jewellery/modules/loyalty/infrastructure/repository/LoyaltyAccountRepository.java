package com.finotech.jewellery.modules.loyalty.infrastructure.repository;

import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID> {

    Optional<LoyaltyAccount> findByCustomerId(UUID customerId);

    /**
     * Locks the account row. Earning and redeeming both take this lock, so two
     * concurrent redemptions cannot each see the same balance and overspend it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccount a where a.customerId = :customerId")
    Optional<LoyaltyAccount> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    @Query("""
            select a from LoyaltyAccount a
            where (:tierId is null or a.tier.id = :tierId)
              and (:minPoints is null or a.pointsBalance >= :minPoints)
            order by a.pointsBalance desc
            """)
    Page<LoyaltyAccount> search(@Param("tierId") UUID tierId,
                                @Param("minPoints") Long minPoints,
                                Pageable pageable);
}
