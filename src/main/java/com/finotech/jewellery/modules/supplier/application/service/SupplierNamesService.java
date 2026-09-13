package com.finotech.jewellery.modules.supplier.application.service;

import com.finotech.jewellery.modules.supplier.application.SupplierNames;
import com.finotech.jewellery.modules.supplier.domain.entity.Supplier;
import com.finotech.jewellery.modules.supplier.infrastructure.repository.SupplierRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves {@link SupplierNames}; depends on the supplier repository alone. */
@Service
@RequiredArgsConstructor
public class SupplierNamesService implements SupplierNames {

    private final SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> namesFor(Collection<UUID> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }
}
