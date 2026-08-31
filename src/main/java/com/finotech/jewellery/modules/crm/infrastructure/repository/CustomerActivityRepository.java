package com.finotech.jewellery.modules.crm.infrastructure.repository;

import com.finotech.jewellery.modules.crm.domain.entity.CustomerActivity;
import com.finotech.jewellery.modules.crm.domain.enums.ActivityType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerActivityRepository extends JpaRepository<CustomerActivity, UUID> {

    List<CustomerActivity> findTop20ByCustomerIdOrderByOccurredAtDesc(UUID customerId);

    @Query("""
            select a from CustomerActivity a
            where (:customerId is null or a.customerId = :customerId)
              and (:activityType is null or a.activityType = :activityType)
              and (:branchId is null or a.branchId = :branchId)
            order by a.occurredAt desc
            """)
    Page<CustomerActivity> search(@Param("customerId") UUID customerId,
                                  @Param("activityType") ActivityType activityType,
                                  @Param("branchId") UUID branchId,
                                  Pageable pageable);
}
