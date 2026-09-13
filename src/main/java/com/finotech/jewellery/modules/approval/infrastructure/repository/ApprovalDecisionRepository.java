package com.finotech.jewellery.modules.approval.infrastructure.repository;

import com.finotech.jewellery.modules.approval.domain.entity.ApprovalDecisionRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecisionRecord, UUID> {

    Optional<ApprovalDecisionRecord> findByIdempotencyKey(String idempotencyKey);
}
