package com.finotech.jewellery.modules.repair.infrastructure.repository;

import com.finotech.jewellery.modules.repair.domain.entity.RepairRequest;
import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {

    @EntityGraph(attributePaths = "history")
    Optional<RepairRequest> findWithHistoryById(UUID id);

    Optional<RepairRequest> findByRequestNumberIgnoreCase(String requestNumber);

    Optional<RepairRequest> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select r from RepairRequest r
            where (:status is null or r.status = :status)
              and (:customerId is null or r.customerId = :customerId)
              and (:branchId is null or r.branchId = :branchId)
              and (:assignedTo is null or r.assignedTo = :assignedTo)
            order by r.receivedDate desc
            """)
    Page<RepairRequest> search(@Param("status") RepairStatus status,
                               @Param("customerId") UUID customerId,
                               @Param("branchId") UUID branchId,
                               @Param("assignedTo") String assignedTo,
                               Pageable pageable);

    /** Jobs promised on or before a date that are not yet ready. */
    @Query("""
            select r from RepairRequest r
            where r.branchId = :branchId
              and r.promisedDate <= :date
              and r.status not in (com.finotech.jewellery.modules.repair.domain.enums.RepairStatus.READY,
                                   com.finotech.jewellery.modules.repair.domain.enums.RepairStatus.DELIVERED,
                                   com.finotech.jewellery.modules.repair.domain.enums.RepairStatus.CANCELLED)
            order by r.promisedDate asc
            """)
    java.util.List<RepairRequest> findOverdue(@Param("branchId") UUID branchId,
                                              @Param("date") java.time.LocalDate date);
}
