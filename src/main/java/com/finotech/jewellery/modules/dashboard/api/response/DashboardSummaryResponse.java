package com.finotech.jewellery.modules.dashboard.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One round trip for the home screen.
 *
 * <p>Every section is permission-filtered on the server: a section the caller
 * may not see is {@code null} and therefore absent from the JSON (the
 * application serialises non-null only), so the client renders nothing rather
 * than "Unavailable". {@code branchId} is null when a super administrator
 * without a branch is looking at the whole business.
 */
public record DashboardSummaryResponse(UUID branchId,
                                       String branchName,
                                       Instant generatedAt,
                                       Sales sales,
                                       Inventory inventory,
                                       InventoryValue inventoryValue,
                                       Transfers transfers,
                                       Approvals approvals,
                                       Repairs repairs,
                                       Procurement procurement,
                                       Notifications notifications,
                                       List<MetalRateSummary> metalRates) {

    /** Settled sales only, so the figure matches the sales report. Requires SALE_VIEW. */
    public record Sales(long todayCount, BigDecimal todayTotal, String currency,
                        BigDecimal monthToDateTotal) {
    }

    /**
     * Requires INVENTORY_VIEW. {@code totalItems} is stock physically on hand
     * (available, reserved, in transit, under repair); {@code lowStockProducts}
     * is the number of monitored locations at or below their threshold.
     */
    public record Inventory(long totalItems, long availableItems, long reservedItems,
                            long lowStockProducts) {
    }

    /** Requires REPORT_VIEW or FINANCE_VIEW: the only readers allowed to see cost. */
    public record InventoryValue(BigDecimal costValue, BigDecimal retailValue, String currency) {
    }

    /**
     * Requires INVENTORY_TRANSFER or INVENTORY_TRANSFER_APPROVE.
     * {@code awaitingSecondApproval} is the subset of {@code pendingApproval}
     * that has one signature and needs a second (dual-authorization vaults).
     */
    public record Transfers(long pendingApproval, long awaitingSecondApproval,
                            long incoming, long outgoing) {
    }

    /**
     * Work waiting on the caller's approval permissions. Only the types the
     * caller may approve appear in {@code byType}; the section is absent when
     * the caller holds none of them.
     */
    public record Approvals(long total, Map<String, Long> byType) {
    }

    /** Requires REPAIR_VIEW. {@code awaitingCustomer} is APPROVAL_PENDING. */
    public record Repairs(long ready, long inProgress, long awaitingCustomer) {
    }

    /**
     * Requires PROCUREMENT_VIEW. {@code pendingOrders} awaits approval;
     * {@code awaitingReceipt} is approved or partially received.
     */
    public record Procurement(long pendingOrders, long awaitingReceipt) {
    }

    /** Always present: the caller's own unread inbox count. */
    public record Notifications(long unread) {
    }

    /** Requires METAL_VIEW. {@code stale} when published more than four hours ago. */
    public record MetalRateSummary(UUID metalId, String metalName, UUID purityId,
                                   String purityCode, String rateType, BigDecimal rate,
                                   String currency, Instant publishedAt, boolean stale) {
    }
}
