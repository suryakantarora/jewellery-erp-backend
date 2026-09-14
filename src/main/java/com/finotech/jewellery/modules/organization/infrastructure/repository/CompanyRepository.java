package com.finotech.jewellery.modules.organization.infrastructure.repository;

import com.finotech.jewellery.modules.organization.domain.entity.Company;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @org.springframework.data.jpa.repository.Query("select c.id from Company c order by c.createdAt, c.id")
    java.util.List<UUID> findAllIds();
}
