package com.finotech.jewellery.modules.sales.infrastructure.repository;

import com.finotech.jewellery.modules.sales.domain.entity.DiscountRequest;
import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountRequestRepository extends JpaRepository<DiscountRequest, UUID> {

    @Query("""
            select d from DiscountRequest d
            where (:branchId is null or d.branchId = :branchId)
              and (:status is null or d.status = :status)
              and (cast(:requestedBy as string) is null or d.createdBy = :requestedBy)
            """)
    Page<DiscountRequest> search(@Param("branchId") UUID branchId,
                                 @Param("status") DiscountRequestStatus status,
                                 @Param("requestedBy") String requestedBy,
                                 Pageable pageable);

    /** Open requests still awaiting a decision, oldest first. */
    @Query("""
            select d from DiscountRequest d
            where d.status = com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus.PENDING
              and (:branchId is null or d.branchId = :branchId)
            order by d.createdAt asc
            """)
    Page<DiscountRequest> findPending(@Param("branchId") UUID branchId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DiscountRequest d
               set d.status = com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus.EXPIRED,
                   d.updatedAt = :now
             where d.status in :openStatuses
               and d.expiresAt <= :now
            """)
    int expireOverdue(@Param("openStatuses") Collection<DiscountRequestStatus> openStatuses,
                      @Param("now") Instant now);
}
