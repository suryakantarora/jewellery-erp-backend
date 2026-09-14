package com.finotech.jewellery.modules.identity.infrastructure.repository;

import com.finotech.jewellery.modules.identity.domain.entity.User;
import java.util.Collection;
import java.util.List;
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

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "branchIds"})
    @Query("select u from User u where u.id = :id and (:companyId is null or u.companyId = :companyId)")
    Optional<User> findWithAuthoritiesByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select u from User u where u.id = :id and (:companyId is null or u.companyId = :companyId)")
    Optional<User> findByIdInCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select distinct u from User u
            where (:companyId is null or u.companyId = :companyId)
              and (cast(:search as string) is null
                   or lower(u.username) like lower(concat('%', cast(:search as string), '%'))
                   or lower(u.fullName) like lower(concat('%', cast(:search as string), '%')))
              and (:branchId is null or :branchId member of u.branchIds)
            """)
    Page<User> search(@Param("companyId") UUID companyId,
                      @Param("search") String search,
                      @Param("branchId") UUID branchId,
                      Pageable pageable);

    @Query("select u.id from User u where lower(u.username) = lower(:username)")
    Optional<UUID> findIdByUsernameIgnoreCase(@Param("username") String username);

    /**
     * Backs {@code UserDirectory}: who can be told about something. The
     * permission join is a left join so a super-admin role with no explicit
     * permission rows still qualifies — but only, like everyone else, when the
     * user is assigned to one of the branches.
     */
    @Query("""
            select distinct u.id from User u
            join u.roles r
            left join r.permissions p
            join u.branchIds b
            where u.status = com.finotech.jewellery.modules.identity.domain.enums.UserStatus.ACTIVE
              and b in :branchIds
              and (r.superAdmin = true or p.code = :permission)
            """)
    List<UUID> findActiveIdsWithPermissionInBranches(@Param("permission") String permission,
                                                     @Param("branchIds") Collection<UUID> branchIds);
}
