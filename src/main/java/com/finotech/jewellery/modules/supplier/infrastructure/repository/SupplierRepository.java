package com.finotech.jewellery.modules.supplier.infrastructure.repository;

import com.finotech.jewellery.modules.supplier.domain.entity.Supplier;
import com.finotech.jewellery.modules.supplier.domain.enums.SupplierStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select s from Supplier s where s.id = :id and (:companyId is null or s.companyId = :companyId)")
    Optional<Supplier> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /**
     * Loads a supplier whose collections are initialised lazily by the calling
     * transaction. See {@code CustomerRepository#findWithDetailsById} — Hibernate
     * cannot fetch several list collections in one query.
     */
    Optional<Supplier> findWithDetailsById(UUID id);

    @Query("""
            select s from Supplier s
            where (:companyId is null or s.companyId = :companyId)
              and (cast(:search as string) is null
                   or lower(s.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(s.code) like lower(concat('%', cast(:search as string), '%')))
              and (:status is null or s.status = :status)
            """)
    Page<Supplier> search(@Param("companyId") UUID companyId,
                          @Param("search") String search,
                          @Param("status") SupplierStatus status,
                          Pageable pageable);
}
