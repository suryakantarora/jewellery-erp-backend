package com.finotech.jewellery.modules.dashboard.application.service;

import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Approvals;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Inventory;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.InventoryValue;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.MetalRateSummary;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Notifications;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Procurement;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Repairs;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Sales;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse.Transfers;
import com.finotech.jewellery.modules.dashboard.infrastructure.repository.DashboardQueryRepository;
import com.finotech.jewellery.modules.dashboard.infrastructure.repository.DashboardQueryRepository.ItemStatusRow;
import com.finotech.jewellery.modules.dashboard.infrastructure.repository.DashboardQueryRepository.SalesRow;
import com.finotech.jewellery.modules.dashboard.infrastructure.repository.DashboardQueryRepository.StatusCountRow;
import com.finotech.jewellery.modules.dashboard.infrastructure.repository.DashboardQueryRepository.TransferRow;
import com.finotech.jewellery.modules.notification.application.service.NotificationInboxService;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the home-screen summary, one section per permission.
 *
 * <p>Nothing here is gated by a single controller permission: every caller
 * with a token gets a response, and each section is included only when the
 * caller holds the permission that would let them read the underlying module.
 * That resolves the "Today's sales" question for sales staff — SALE_VIEW is
 * enough, because the figure comes from the sales table directly rather than
 * from the REPORT_VIEW-guarded report.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    static final Duration RATE_STALE_AFTER = Duration.ofHours(4);

    private static final Set<String> ON_HAND =
            Set.of("AVAILABLE", "RESERVED", "IN_TRANSIT", "UNDER_REPAIR");
    private static final Set<String> SELLABLE = Set.of("AVAILABLE", "RESERVED");

    private final DashboardQueryRepository queries;
    private final NotificationInboxService inboxService;
    private final DashboardCache cache;

    public DashboardSummaryResponse summary(UUID requestedBranchId, boolean refresh) {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        UUID branchId = resolveBranch(user, requestedBranchId);
        return cache.get(user.userId(), branchId, refresh, () -> build(user, branchId));
    }

    /**
     * Explicit parameter, then the {@code X-Branch-Id} header, then the user's
     * primary branch, then their only branch. A super administrator with no
     * branch sees the whole business; anyone else must say which branch.
     */
    private UUID resolveBranch(AuthenticatedUser user, UUID requested) {
        UUID branchId = requested;
        if (branchId == null) {
            branchId = SecurityUtils.currentBranchId().orElse(null);
        }
        if (branchId == null) {
            branchId = queries.primaryBranchOf(user.userId())
                    .filter(user::hasAccessToBranch)
                    .orElse(null);
        }
        if (branchId == null && user.branchIds().size() == 1) {
            branchId = user.branchIds().iterator().next();
        }
        if (branchId == null) {
            if (user.superAdmin()) {
                return null;
            }
            throw new ValidationException("branchId is required: you work in "
                    + user.branchIds().size() + " branches and have no primary branch");
        }
        if (!user.hasAccessToBranch(branchId)) {
            throw new ForbiddenException("No access to branch " + branchId);
        }
        return branchId;
    }

    private DashboardSummaryResponse build(AuthenticatedUser user, UUID branchId) {
        String branchName = branchId == null ? null : queries.branchName(branchId)
                .orElseThrow(() -> new NotFoundException("Branch " + branchId + " not found"));
        Set<String> perms = user.permissions();
        LocalDate today = LocalDate.now();

        Sales sales = perms.contains("SALE_VIEW") ? sales(branchId, today) : null;

        Inventory inventory = null;
        InventoryValue inventoryValue = null;
        boolean seesInventory = perms.contains("INVENTORY_VIEW");
        boolean seesValue = perms.contains("REPORT_VIEW") || perms.contains("FINANCE_VIEW");
        if (seesInventory || seesValue) {
            List<ItemStatusRow> rows = queries.itemsByStatus(branchId);
            if (seesInventory) {
                inventory = inventory(rows, branchId);
            }
            if (seesValue) {
                inventoryValue = inventoryValue(rows);
            }
        }

        Transfers transfers = null;
        boolean approvesTransfers = perms.contains("INVENTORY_TRANSFER_APPROVE");
        boolean seesTransfers = perms.contains("INVENTORY_TRANSFER") || approvesTransfers;
        if (seesTransfers) {
            transfers = transfers(queries.openTransfers(branchId), branchId);
        }

        Map<String, Long> approvalsByType = new LinkedHashMap<>();
        if (approvesTransfers) {
            approvalsByType.put("TRANSFER", transfers.pendingApproval());
        }
        List<StatusCountRow> orders = null;
        boolean approvesProcurement = perms.contains("PROCUREMENT_APPROVE");
        boolean seesProcurement = perms.contains("PROCUREMENT_VIEW");
        if (approvesProcurement || seesProcurement) {
            orders = queries.purchaseOrdersByStatus(branchId);
        }
        if (approvesProcurement) {
            approvalsByType.put("PURCHASE_ORDER", countOf(orders, "PENDING_APPROVAL"));
            approvalsByType.put("REQUISITION", queries.requisitionsPendingApproval(branchId));
        }
        if (perms.contains("EXCHANGE_APPROVE")) {
            approvalsByType.put("EXCHANGE", queries.exchangesPendingApproval(branchId));
        }
        if (perms.contains("STOCK_COUNT_APPROVE")) {
            approvalsByType.put("STOCK_COUNT", queries.stockCountsPendingReview(branchId));
        }
        if (perms.contains("PROCUREMENT_RECEIVE")) {
            approvalsByType.put("GOODS_RECEIPT", queries.goodsReceiptsPendingQualityCheck(branchId));
        }
        Approvals approvals = approvalsByType.isEmpty() ? null : new Approvals(
                approvalsByType.values().stream().mapToLong(Long::longValue).sum(),
                approvalsByType);

        Repairs repairs = perms.contains("REPAIR_VIEW")
                ? repairs(queries.repairsByStatus(branchId)) : null;

        Procurement procurement = seesProcurement ? new Procurement(
                countOf(orders, "PENDING_APPROVAL"),
                countOf(orders, "APPROVED") + countOf(orders, "PARTIALLY_RECEIVED")) : null;

        Notifications notifications = new Notifications(inboxService.unreadCount());

        List<MetalRateSummary> metalRates = perms.contains("METAL_VIEW")
                ? metalRates(branchId, today) : null;

        return new DashboardSummaryResponse(branchId, branchName, Instant.now(), sales,
                inventory, inventoryValue, transfers, approvals, repairs, procurement,
                notifications, metalRates);
    }

    private Sales sales(UUID branchId, LocalDate today) {
        SalesRow row = queries.sales(branchId, today);
        return new Sales(row.todayCount(), row.todayTotal(),
                row.currency() == null ? "LAK" : row.currency(), row.monthToDateTotal());
    }

    private Inventory inventory(List<ItemStatusRow> rows, UUID branchId) {
        long total = rows.stream().filter(r -> ON_HAND.contains(r.status()))
                .mapToLong(ItemStatusRow::count).sum();
        return new Inventory(total, countItems(rows, "AVAILABLE"), countItems(rows, "RESERVED"),
                queries.lowStockLocations(branchId));
    }

    private InventoryValue inventoryValue(List<ItemStatusRow> rows) {
        BigDecimal cost = rows.stream().filter(r -> ON_HAND.contains(r.status()))
                .map(ItemStatusRow::costValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal retail = rows.stream().filter(r -> SELLABLE.contains(r.status()))
                .map(ItemStatusRow::retailValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = rows.stream().map(ItemStatusRow::currency)
                .filter(c -> c != null).findFirst().orElse("LAK");
        return new InventoryValue(cost, retail, currency);
    }

    private Transfers transfers(List<TransferRow> rows, UUID branchId) {
        long pending = 0;
        long second = 0;
        long incoming = 0;
        long outgoing = 0;
        for (TransferRow row : rows) {
            if ("PENDING_APPROVAL".equals(row.status())) {
                pending += row.count();
                if (row.firstApproved()) {
                    second += row.count();
                }
            } else if ("DISPATCHED".equals(row.status())) {
                if (branchId == null || branchId.equals(row.toBranchId())) {
                    incoming += row.count();
                }
                if (branchId == null || branchId.equals(row.fromBranchId())) {
                    outgoing += row.count();
                }
            }
        }
        return new Transfers(pending, second, incoming, outgoing);
    }

    private Repairs repairs(List<StatusCountRow> rows) {
        return new Repairs(countOf(rows, "READY"), countOf(rows, "IN_PROGRESS"),
                countOf(rows, "APPROVAL_PENDING"));
    }

    private List<MetalRateSummary> metalRates(UUID branchId, LocalDate today) {
        Instant staleBefore = Instant.now().minus(RATE_STALE_AFTER);
        return queries.currentSellingRates(branchId, today).stream()
                .map(r -> new MetalRateSummary(r.metalId(), r.metalName(), r.purityId(),
                        r.purityCode(), r.rateType(), r.rate(), r.currency(), r.publishedAt(),
                        r.publishedAt().isBefore(staleBefore)))
                .toList();
    }

    private static long countOf(List<StatusCountRow> rows, String status) {
        if (rows == null) {
            return 0;
        }
        return rows.stream().filter(r -> status.equals(r.status()))
                .mapToLong(StatusCountRow::count).sum();
    }

    private static long countItems(List<ItemStatusRow> rows, String status) {
        return rows.stream().filter(r -> status.equals(r.status()))
                .mapToLong(ItemStatusRow::count).sum();
    }
}
