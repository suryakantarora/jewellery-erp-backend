package com.finotech.jewellery.modules.organization.infrastructure.repository;

import com.finotech.jewellery.modules.organization.domain.entity.Company;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByCodeIgnoreCase(String code);
}
