package com.finotech.jewellery.modules.organization.infrastructure.repository;

import com.finotech.jewellery.modules.organization.domain.entity.Branch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<Branch> findAllByCompanyId(UUID companyId);

    @Query("""
            select b from Branch b
            where (:companyId is null or b.company.id = :companyId)
              and (cast(:search as string) is null
                   or lower(b.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(b.code) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Branch> search(@Param("companyId") UUID companyId,
                        @Param("search") String search,
                        Pageable pageable);
}
