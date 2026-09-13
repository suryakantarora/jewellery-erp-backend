package com.finotech.jewellery.modules.inventory.application.service;

import com.finotech.jewellery.modules.inventory.application.ItemDirectory;
import com.finotech.jewellery.modules.inventory.domain.entity.ItemImage;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.JewelleryItemRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Batch item summaries for other modules' list rows. */
@Service
@RequiredArgsConstructor
public class ItemDirectoryService implements ItemDirectory {

    private final JewelleryItemRepository itemRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ItemSummary> summariesFor(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ItemSummary> summaries = new HashMap<>();
        for (JewelleryItem item : itemRepository.findAllById(itemIds)) {
            String primaryImageKey = item.getImages().stream()
                    .filter(ItemImage::isPrimaryImage)
                    .map(ItemImage::getStorageKey)
                    .findFirst()
                    .orElse(null);
            summaries.put(item.getId(), new ItemSummary(item.getId(), item.getItemCode(),
                    item.getProductId(), item.getDesignId(), item.getCurrentPrice(),
                    item.getCurrency(), item.getStatus().name(), item.getCurrentBranchId(),
                    primaryImageKey));
        }
        return summaries;
    }
}
