package com.finotech.jewellery.modules.crm.infrastructure.repository;

import com.finotech.jewellery.modules.crm.domain.entity.Campaign;
import com.finotech.jewellery.modules.crm.domain.enums.CampaignStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select c from Campaign c
            where (:status is null or c.status = :status)
              and (:branchId is null or c.branchId = :branchId)
            order by c.createdAt desc
            """)
    Page<Campaign> search(@Param("status") CampaignStatus status,
                          @Param("branchId") UUID branchId,
                          Pageable pageable);
}
