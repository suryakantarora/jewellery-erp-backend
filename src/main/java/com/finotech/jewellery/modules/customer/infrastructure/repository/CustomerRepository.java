package com.finotech.jewellery.modules.customer.infrastructure.repository;

import com.finotech.jewellery.modules.customer.domain.entity.Customer;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerStatus;
import com.finotech.jewellery.modules.customer.domain.enums.KycStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByCustomerCodeIgnoreCase(String customerCode);

    boolean existsByPhone(String phone);

    Optional<Customer> findByPhone(String phone);

    /**
     * Loads a customer whose collections are initialised lazily by the calling
     * transaction.
     *
     * <p>Deliberately not a multi-collection entity graph: Hibernate cannot fetch
     * more than one list in a single query, and doing so fails at runtime rather
     * than at startup. Each collection is batch-loaded instead.
     */
    Optional<Customer> findWithDetailsById(UUID id);

    /**
     * Active customers matching the demographic filters a CRM segment can use.
     * Birthday month is matched against the stored date of birth.
     */
    @Query("""
            select c from Customer c
            where c.status = com.finotech.jewellery.modules.customer.domain.enums.CustomerStatus.ACTIVE
              and (:branchId is null or c.registeredBranchId = :branchId)
              and (:birthdayMonth is null
                   or (c.dateOfBirth is not null
                       and extract(month from c.dateOfBirth) = :birthdayMonth))
            """)
    java.util.List<Customer> findSegmentCandidates(@Param("branchId") UUID branchId,
                                                   @Param("birthdayMonth") Integer birthdayMonth);

    @Query("""
            select c from Customer c
            where (cast(:search as string) is null
                   or lower(c.fullName) like lower(concat('%', cast(:search as string), '%'))
                   or lower(c.customerCode) like lower(concat('%', cast(:search as string), '%'))
                   or c.phone like concat('%', cast(:search as string), '%'))
              and (:status is null or c.status = :status)
              and (:kycStatus is null or c.kycStatus = :kycStatus)
              and (:branchId is null or c.registeredBranchId = :branchId)
            """)
    Page<Customer> search(@Param("search") String search,
                          @Param("status") CustomerStatus status,
                          @Param("kycStatus") KycStatus kycStatus,
                          @Param("branchId") UUID branchId,
                          Pageable pageable);
}
