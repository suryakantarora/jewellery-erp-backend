package com.finotech.jewellery.modules.identity.infrastructure.repository;

import com.finotech.jewellery.modules.identity.domain.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "branchIds"})
    Optional<User> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "branchIds"})
    Optional<User> findWithAuthoritiesById(UUID id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select distinct u from User u
            where (cast(:search as string) is null
                   or lower(u.username) like lower(concat('%', cast(:search as string), '%'))
                   or lower(u.fullName) like lower(concat('%', cast(:search as string), '%')))
              and (:branchId is null or :branchId member of u.branchIds)
            """)
    Page<User> search(@Param("search") String search,
                      @Param("branchId") UUID branchId,
                      Pageable pageable);
}
