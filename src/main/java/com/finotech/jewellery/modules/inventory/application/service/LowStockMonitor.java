package com.finotech.jewellery.modules.inventory.application.service;

import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.inventory.infrastructure.repository.JewelleryItemRepository;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises {@code LOW_STOCK} for locations that have run down.
 *
 * <p>Only counts items that are actually sellable: reserved, in-transit and
 * under-repair stock is not available to a customer, and counting it would mean
 * the alert never fires when it matters.
 *
 * <p>Only locations with a threshold configured are monitored. A vault holding
 * two pieces is not low on stock, so silence is the correct default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockMonitor {

    private final OrganizationDirectory organizationDirectory;
    private final JewelleryItemRepository itemRepository;
    private final DomainEventPublisher events;

    @Scheduled(cron = "${jewellery.inventory.low-stock-cron:0 0 7 * * *}")
    @Transactional(readOnly = true)
    public void checkStockLevels() {
        List<OrganizationDirectory.LocationView> monitored =
                organizationDirectory.monitoredLocations();
        if (monitored.isEmpty()) {
            return;
        }

        int raised = 0;
        for (OrganizationDirectory.LocationView location : monitored) {
            if (!location.canHoldStock() || location.lowStockThreshold() == null) {
                continue;
            }
            long available = itemRepository.countByCurrentLocationIdAndStatus(
                    location.id(), ItemStatus.AVAILABLE);

            if (available <= location.lowStockThreshold()) {
                events.publish(new DomainEvents.LowStock(location.branchId(), location.id(),
                        available, location.lowStockThreshold()));
                raised++;
                log.info("Low stock at {}: {} available against a threshold of {}",
                        location.code(), available, location.lowStockThreshold());
            }
        }
        if (raised > 0) {
            log.info("Raised {} low-stock alert(s)", raised);
        }
    }
}
