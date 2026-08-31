package com.finotech.jewellery.modules.metal.infrastructure.repository;

import com.finotech.jewellery.modules.metal.domain.entity.ScrapMetal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapMetalRepository extends JpaRepository<ScrapMetal, UUID> {

    boolean existsByBatchNumberIgnoreCase(String batchNumber);

    @Query("""
            select s from ScrapMetal s
            where (:metalId is null or s.metal.id = :metalId)
              and (:locationId is null or s.locationId = :locationId)
              and (cast(:status as string) is null or s.status = :status)
            order by s.receivedDate desc
            """)
    Page<ScrapMetal> search(@Param("metalId") UUID metalId,
                            @Param("locationId") UUID locationId,
                            @Param("status") String status,
                            Pageable pageable);
}
