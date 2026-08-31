package com.finotech.jewellery.modules.loyalty.infrastructure.repository;

import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyTransaction;
import com.finotech.jewellery.modules.loyalty.domain.enums.LoyaltyTransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    Page<LoyaltyTransaction> findAllByCustomerIdOrderByOccurredAtDesc(UUID customerId,
                                                                      Pageable pageable);

    /** Guards against awarding points twice for the same sale. */
    boolean existsByReferenceTypeAndReferenceIdAndTransactionType(
            String referenceType, String referenceId, LoyaltyTransactionType transactionType);

    List<LoyaltyTransaction> findAllByReferenceTypeAndReferenceId(String referenceType,
                                                                  String referenceId);

    /** Awarded points that have reached their expiry date and not yet been expired. */
    @Query("""
            select t from LoyaltyTransaction t
            where t.transactionType = com.finotech.jewellery.modules.loyalty.domain.enums.LoyaltyTransactionType.EARN
              and t.expired = false
              and t.expiresOn is not null
              and t.expiresOn < :today
            order by t.expiresOn asc
            """)
    List<LoyaltyTransaction> findExpiring(@Param("today") LocalDate today, Pageable pageable);
}
