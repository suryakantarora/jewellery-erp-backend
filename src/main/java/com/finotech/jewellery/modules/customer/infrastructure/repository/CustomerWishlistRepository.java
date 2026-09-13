package com.finotech.jewellery.modules.customer.infrastructure.repository;

import com.finotech.jewellery.modules.customer.domain.entity.CustomerWishlistEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerWishlistRepository extends JpaRepository<CustomerWishlistEntry, UUID> {

    List<CustomerWishlistEntry> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<CustomerWishlistEntry> findByCustomerIdAndJewelleryItemId(UUID customerId,
                                                                       UUID jewelleryItemId);

    Optional<CustomerWishlistEntry> findByIdAndCustomerId(UUID id, UUID customerId);
}
