package com.finotech.jewellery.modules.crm.infrastructure.repository;

import com.finotech.jewellery.modules.crm.domain.entity.CustomerSegment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<CustomerSegment> findAllByOrderByCodeAsc();
}
