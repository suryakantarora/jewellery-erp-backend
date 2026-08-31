package com.finotech.jewellery.modules.payment.infrastructure.repository;

import com.finotech.jewellery.modules.payment.domain.entity.Payment;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllBySaleIdOrderByCreatedAtAsc(UUID saleId);

    @Query("""
            select p from Payment p
            where (:saleId is null or p.saleId = :saleId)
              and (:branchId is null or p.branchId = :branchId)
              and (:status is null or p.status = :status)
              and (cast(:from as date) is null or cast(p.capturedAt as date) >= :from)
              and (cast(:to as date) is null or cast(p.capturedAt as date) <= :to)
            order by p.createdAt desc
            """)
    Page<Payment> search(@Param("saleId") UUID saleId,
                         @Param("branchId") UUID branchId,
                         @Param("status") PaymentStatus status,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);

    /** Cash-up figure per method for the daily closing. */
    @Query("""
            select p.method, coalesce(sum(case when p.direction =
                     com.finotech.jewellery.modules.payment.domain.enums.PaymentDirection.REFUND
                   then -p.amount else p.amount end), 0)
            from Payment p
            where p.branchId = :branchId
              and p.status = com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus.CAPTURED
              and cast(p.capturedAt as date) = :date
            group by p.method
            """)
    List<Object[]> totalsByMethod(@Param("branchId") UUID branchId, @Param("date") LocalDate date);

    @Query("""
            select coalesce(sum(case when p.direction =
                     com.finotech.jewellery.modules.payment.domain.enums.PaymentDirection.REFUND
                   then -p.amount else p.amount end), 0)
            from Payment p
            where p.saleId = :saleId
              and p.status = com.finotech.jewellery.modules.payment.domain.enums.PaymentStatus.CAPTURED
            """)
    BigDecimal netCapturedForSale(@Param("saleId") UUID saleId);
}
