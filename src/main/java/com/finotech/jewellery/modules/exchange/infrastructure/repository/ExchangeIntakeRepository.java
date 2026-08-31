package com.finotech.jewellery.modules.exchange.infrastructure.repository;

import com.finotech.jewellery.modules.exchange.domain.entity.ExchangeIntake;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeIntakeRepository extends JpaRepository<ExchangeIntake, UUID> {

    @Query("""
            select e from ExchangeIntake e
            where (:status is null or e.status = :status)
              and (:exchangeType is null or e.exchangeType = :exchangeType)
              and (:customerId is null or e.customerId = :customerId)
              and (:branchId is null or e.branchId = :branchId)
            order by e.receivedDate desc
            """)
    Page<ExchangeIntake> search(@Param("status") ExchangeStatus status,
                                @Param("exchangeType") ExchangeType exchangeType,
                                @Param("customerId") UUID customerId,
                                @Param("branchId") UUID branchId,
                                Pageable pageable);
}
