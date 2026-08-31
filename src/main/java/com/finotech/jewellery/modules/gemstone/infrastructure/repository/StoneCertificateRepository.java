package com.finotech.jewellery.modules.gemstone.infrastructure.repository;

import com.finotech.jewellery.modules.gemstone.domain.entity.StoneCertificate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoneCertificateRepository extends JpaRepository<StoneCertificate, UUID> {

    boolean existsByCertificateNumberIgnoreCase(String certificateNumber);

    Optional<StoneCertificate> findByCertificateNumberIgnoreCase(String certificateNumber);

    @Query("""
            select c from StoneCertificate c
            where (cast(:search as string) is null
                   or lower(c.certificateNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(c.issuingLab) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<StoneCertificate> search(@Param("search") String search, Pageable pageable);
}
