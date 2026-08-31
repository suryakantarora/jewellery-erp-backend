package com.finotech.jewellery.modules.sales.infrastructure.repository;

import com.finotech.jewellery.modules.sales.domain.entity.Sale;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<Sale> findWithLinesById(UUID id);

    /**
     * Locks the sale row. Payment capture takes this lock so two concurrent
     * payments cannot both read a stale paid total and over-settle the sale.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") UUID id);

    Optional<Sale> findByExternalReference(String externalReference);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("""
            select s from Sale s
            where (:status is null or s.status = :status)
              and (:customerId is null or s.customerId = :customerId)
              and (:branchId is null or s.branchId = :branchId)
              and (cast(:from as date) is null or s.saleDate >= :from)
              and (cast(:to as date) is null or s.saleDate <= :to)
            """)
    Page<Sale> search(@Param("status") SaleStatus status,
                      @Param("customerId") UUID customerId,
                      @Param("branchId") UUID branchId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      Pageable pageable);

    /** Backs the daily closing report. */
    @Query("""
            select coalesce(sum(s.totalAmount), 0) from Sale s
            where s.branchId = :branchId and s.saleDate = :date
              and s.status in (com.finotech.jewellery.modules.sales.domain.enums.SaleStatus.CONFIRMED,
                               com.finotech.jewellery.modules.sales.domain.enums.SaleStatus.DELIVERED)
            """)
    java.math.BigDecimal totalSalesFor(@Param("branchId") UUID branchId,
                                       @Param("date") LocalDate date);

    long countByBranchIdAndSaleDate(UUID branchId, LocalDate saleDate);

    /**
     * Purchase aggregates per customer over settled sales only. Returns rows of
     * customerId, count, total, first date, last date.
     */
    @Query("""
            select s.customerId, count(s), coalesce(sum(s.totalAmount), 0),
                   min(s.saleDate), max(s.saleDate)
            from Sale s
            where s.customerId in :customerIds
              and s.status in (com.finotech.jewellery.modules.sales.domain.enums.SaleStatus.CONFIRMED,
                               com.finotech.jewellery.modules.sales.domain.enums.SaleStatus.DELIVERED,
                               com.finotech.jewellery.modules.sales.domain.enums.SaleStatus.PARTIALLY_RETURNED)
            group by s.customerId
            """)
    List<Object[]> purchaseSummaries(@Param("customerIds") Collection<UUID> customerIds);
}
