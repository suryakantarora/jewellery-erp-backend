package com.finotech.jewellery.modules.approval.infrastructure.repository;

import com.finotech.jewellery.modules.approval.domain.entity.ApprovalInformationRequest;
import com.finotech.jewellery.modules.approval.domain.enums.ApprovalType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalInformationRequestRepository
        extends JpaRepository<ApprovalInformationRequest, UUID> {

    List<ApprovalInformationRequest> findAllByApprovalTypeAndReferenceIdOrderByCreatedAtAsc(
            ApprovalType approvalType, UUID referenceId);

    /** Open questions across many records, for flagging a pending list in one query. */
    List<ApprovalInformationRequest> findAllByApprovalTypeAndReferenceIdInAndAnsweredAtIsNull(
            ApprovalType approvalType, Collection<UUID> referenceIds);
}
