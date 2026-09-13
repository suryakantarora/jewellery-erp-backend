package com.finotech.jewellery.modules.warehouse.application.service;

import com.finotech.jewellery.modules.warehouse.application.BinDirectory;
import com.finotech.jewellery.modules.warehouse.domain.entity.StorageBin;
import com.finotech.jewellery.modules.warehouse.infrastructure.repository.StorageBinRepository;
import com.finotech.jewellery.shared.exception.NotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves {@link BinDirectory} to other modules.
 *
 * <p>Deliberately a separate bean rather than another role for
 * {@code WarehouseService}. Warehouse already depends on inventory through
 * {@code InventoryOperations}, so having inventory depend back on
 * {@code WarehouseService} closed a constructor cycle and the context refused
 * to start. This component depends on the bin repository alone, which is all
 * the port needs and all it should reach for.
 */
@Service
@RequiredArgsConstructor
public class BinDirectoryService implements BinDirectory {

    private final StorageBinRepository binRepository;

    @Override
    @Transactional(readOnly = true)
    public BinView requireBin(UUID binId) {
        StorageBin bin = binRepository.findById(binId)
                .orElseThrow(() -> new NotFoundException("Storage bin not found"));
        return new BinView(bin.getId(), bin.getLocationId(), bin.getCode(), bin.getName(),
                bin.isActive());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> codesFor(Collection<UUID> binIds) {
        if (binIds == null || binIds.isEmpty()) {
            return Map.of();
        }
        return binRepository.findAllById(binIds).stream()
                .collect(Collectors.toMap(StorageBin::getId, StorageBin::getCode));
    }
}
