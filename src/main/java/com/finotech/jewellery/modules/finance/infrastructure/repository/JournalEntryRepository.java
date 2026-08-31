package com.finotech.jewellery.modules.finance.infrastructure.repository;

import com.finotech.jewellery.modules.finance.domain.entity.JournalEntry;
import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.JournalStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByEntryNumberIgnoreCase(String entryNumber);

    /**
     * Guards against posting the same business document twice — a redelivered
     * event must not double the ledger.
     */
    boolean existsByReferenceTypeAndReferenceIdAndSourceAndStatus(
            String referenceType, String referenceId, JournalSource source, JournalStatus status);

    @Query("""
            select e from JournalEntry e
            where (:status is null or e.status = :status)
              and (:source is null or e.source = :source)
              and (:branchId is null or e.branchId = :branchId)
              and (cast(:from as date) is null or e.entryDate >= :from)
              and (cast(:to as date) is null or e.entryDate <= :to)
            order by e.entryDate desc, e.entryNumber desc
            """)
    Page<JournalEntry> search(@Param("status") JournalStatus status,
                              @Param("source") JournalSource source,
                              @Param("branchId") UUID branchId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              Pageable pageable);
}
