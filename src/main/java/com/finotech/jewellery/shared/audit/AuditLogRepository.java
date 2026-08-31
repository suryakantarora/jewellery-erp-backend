package com.finotech.jewellery.shared.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select a from AuditLog a
            where (cast(:entityType as string) is null or a.entityType = :entityType)
              and (cast(:entityId as string) is null or a.entityId = :entityId)
              and (:userId is null or a.userId = :userId)
              and (cast(:from as timestamp) is null or a.occurredAt >= :from)
              and (cast(:to as timestamp) is null or a.occurredAt <= :to)
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(@Param("entityType") String entityType,
                          @Param("entityId") String entityId,
                          @Param("userId") UUID userId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
