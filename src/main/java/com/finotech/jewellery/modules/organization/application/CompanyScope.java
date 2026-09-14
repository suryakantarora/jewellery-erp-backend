package com.finotech.jewellery.modules.organization.application;

import java.util.UUID;

/**
 * The tenant boundary, for services that own company-scoped data.
 *
 * <p>Reads pass {@link #currentOrNull()} into their repository query with the
 * {@code (:companyId is null or e.companyId = :companyId)} idiom — explicit in
 * every query rather than a Hibernate filter, so the scope is visible and
 * testable where it applies. Creates stamp {@link #resolveForCreate(UUID)}.
 */
public interface CompanyScope {

    /**
     * The company the caller's reads are confined to, or {@code null} when they
     * may see every company (a super administrator without a home company, or
     * code running for the platform outside a request).
     */
    UUID currentOrNull();

    /**
     * The company a new record belongs to.
     *
     * <p>An ordinary user's own company; naming a different one is refused. A
     * super administrator with a home company uses it; without one they must
     * name the company in the request, or work inside an {@code X-Branch-Id}
     * whose branch decides it, or there must be exactly one company on the
     * platform. Anything else is a validation error rather than a guess.
     */
    UUID resolveForCreate(UUID requestedCompanyId);

    /** The company that owns a branch. Throws NotFound when the branch does not exist. */
    UUID requireCompanyOfBranch(UUID branchId);

    /**
     * Fails with a validation error when the branch belongs to a different
     * company, or does not exist. Used when a company-scoped record is tied to
     * a branch (a user's branch grants, a customer's registered branch).
     */
    void requireBranchInCompany(UUID branchId, UUID companyId);
}
