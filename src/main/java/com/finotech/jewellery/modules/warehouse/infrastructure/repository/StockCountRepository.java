package com.finotech.jewellery.modules.warehouse.infrastructure.repository;

import com.finotech.jewellery.modules.warehouse.domain.entity.StockCount;
import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockCountRepository extends JpaRepository<StockCount, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<StockCount> findWithLinesById(UUID id);

    /** Guards against two counts running on the same location at once. */
    boolean existsByLocationIdAndStatusIn(UUID locationId,
                                          java.util.Collection<StockCountStatus> statuses);

    @Query("""
            select c from StockCount c
            where (:status is null or c.status = :status)
              and (:locationId is null or c.locationId = :locationId)
              and (:branchId is null or c.branchId = :branchId)
            order by c.countDate desc
            """)
    Page<StockCount> search(@Param("status") StockCountStatus status,
                            @Param("locationId") UUID locationId,
                            @Param("branchId") UUID branchId,
                            Pageable pageable);
}
