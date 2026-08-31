package com.finotech.jewellery.modules.crm.infrastructure.repository;

import com.finotech.jewellery.modules.crm.domain.entity.FollowUp;
import com.finotech.jewellery.modules.crm.domain.enums.FollowUpStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUp> findAllByCustomerIdAndStatusOrderByDueDateAsc(UUID customerId,
                                                                 FollowUpStatus status);

    @Query("""
            select f from FollowUp f
            where (:status is null or f.status = :status)
              and (:customerId is null or f.customerId = :customerId)
              and (cast(:assignedTo as string) is null or f.assignedTo = :assignedTo)
              and (:branchId is null or f.branchId = :branchId)
              and (cast(:dueBefore as date) is null or f.dueDate <= :dueBefore)
            order by f.dueDate asc
            """)
    Page<FollowUp> search(@Param("status") FollowUpStatus status,
                          @Param("customerId") UUID customerId,
                          @Param("assignedTo") String assignedTo,
                          @Param("branchId") UUID branchId,
                          @Param("dueBefore") LocalDate dueBefore,
                          Pageable pageable);

    long countByAssignedToAndStatus(String assignedTo, FollowUpStatus status);
}
