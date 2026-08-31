package com.finotech.jewellery.modules.customer.application;

import java.util.UUID;

/**
 * Published contract for Sales, CRM and Loyalty.
 */
public interface CustomerDirectory {

    CustomerView requireCustomer(UUID customerId);

    /** Fails when the customer is inactive or blacklisted. */
    CustomerView requireTransactableCustomer(UUID customerId);

    /**
     * Active customers matching the simple demographic filters a segment can
     * express. Spend-based criteria are applied by the caller against
     * purchase history, which this module does not own.
     */
    java.util.List<CustomerView> findSegmentCandidates(UUID branchId, Integer birthdayMonth);

    record CustomerView(UUID id, String customerCode, String fullName, String phone,
                        boolean transactable, boolean kycVerified) {
    }
}
