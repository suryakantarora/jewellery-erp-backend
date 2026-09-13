package com.finotech.jewellery.modules.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse;
import com.finotech.jewellery.modules.dashboard.application.service.DashboardCache;
import com.finotech.jewellery.modules.dashboard.application.service.DashboardService;
import com.finotech.jewellery.modules.sales.domain.entity.Sale;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import com.finotech.jewellery.modules.sales.infrastructure.repository.SaleRepository;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * The summary is one call whose sections are decided by permission on the
 * server, so a role's home screen is a consequence of its grants and nothing
 * the client has to know.
 */
@Import(CommerceFixture.class)
class DashboardSummaryIntegrationTest extends IntegrationTestBase {

    @Autowired private DashboardService dashboardService;
    @Autowired private DashboardCache cache;
    @Autowired private CommerceFixture fixture;
    @Autowired private SaleRepository saleRepository;

    private CommerceFixture.World world;

    @BeforeEach
    void setUp() {
        cache.clear();
        TestSecurity.authenticateAsSuperAdmin();
        world = fixture.create(new BigDecimal("1000"), new BigDecimal("50"), BigDecimal.ZERO, null);
        fixture.availableItem(world, new BigDecimal("10.000"));
        fixture.availableItem(world, new BigDecimal("12.000"));
        TestSecurity.clear();
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("SALE_VIEW alone is enough to see today's sales, and nothing else")
    void salesStaffSeeTodaysTotal() {
        TestSecurity.authenticateAsSuperAdmin();
        confirmedSale(new BigDecimal("2500000.0000"));
        confirmedSale(new BigDecimal("1500000.0000"));
        TestSecurity.clear();

        TestSecurity.authenticateAs(Set.of("SALE_VIEW"), Set.of(world.branchId()));
        DashboardSummaryResponse summary = dashboardService.summary(null, false);

        assertThat(summary.branchId()).isEqualTo(world.branchId());
        assertThat(summary.branchName()).isEqualTo("Test Branch");
        assertThat(summary.sales()).isNotNull();
        assertThat(summary.sales().todayCount()).isEqualTo(2);
        assertThat(summary.sales().todayTotal()).isEqualByComparingTo("4000000");
        assertThat(summary.sales().monthToDateTotal()).isEqualByComparingTo("4000000");
        assertThat(summary.sales().currency()).isEqualTo("LAK");
        assertThat(summary.notifications()).isNotNull();

        assertThat(summary.inventory()).isNull();
        assertThat(summary.inventoryValue()).isNull();
        assertThat(summary.transfers()).isNull();
        assertThat(summary.approvals()).isNull();
        assertThat(summary.repairs()).isNull();
        assertThat(summary.procurement()).isNull();
        assertThat(summary.metalRates()).isNull();
    }

    @Test
    @DisplayName("inventory counts need INVENTORY_VIEW; the cost figure needs REPORT_VIEW or FINANCE_VIEW")
    void inventoryValueIsGatedSeparately() {
        TestSecurity.authenticateAs(Set.of("INVENTORY_VIEW", "METAL_VIEW"), Set.of(world.branchId()));
        DashboardSummaryResponse forStaff = dashboardService.summary(world.branchId(), false);
        assertThat(forStaff.inventory()).isNotNull();
        assertThat(forStaff.inventory().totalItems()).isEqualTo(2);
        assertThat(forStaff.inventory().availableItems()).isEqualTo(2);
        assertThat(forStaff.inventory().reservedItems()).isZero();
        assertThat(forStaff.inventoryValue()).isNull();
        assertThat(forStaff.sales()).isNull();
        assertThat(forStaff.metalRates()).isNotNull();
        assertThat(forStaff.metalRates())
                .anySatisfy(rate -> {
                    assertThat(rate.metalId()).isEqualTo(world.metalId());
                    assertThat(rate.purityId()).isEqualTo(world.purityId());
                    assertThat(rate.rateType()).isEqualTo("SELLING");
                    assertThat(rate.rate()).isEqualByComparingTo("1000");
                    assertThat(rate.stale()).isFalse();
                });
        TestSecurity.clear();

        TestSecurity.authenticateAs(Set.of("FINANCE_VIEW"), Set.of(world.branchId()));
        DashboardSummaryResponse forFinance = dashboardService.summary(world.branchId(), false);
        assertThat(forFinance.inventory()).isNull();
        assertThat(forFinance.inventoryValue()).isNotNull();
        assertThat(forFinance.inventoryValue().costValue()).isNotNull();
        assertThat(forFinance.inventoryValue().retailValue()).isNotNull();
        assertThat(forFinance.inventoryValue().currency()).isEqualTo("LAK");
    }

    @Test
    @DisplayName("approval counts appear only for the types the caller may approve")
    void approvalsFollowApprovePermissions() {
        TestSecurity.authenticateAs(Set.of("INVENTORY_TRANSFER_APPROVE", "EXCHANGE_APPROVE"),
                Set.of(world.branchId()));
        DashboardSummaryResponse summary = dashboardService.summary(world.branchId(), false);

        assertThat(summary.transfers()).isNotNull();
        assertThat(summary.approvals()).isNotNull();
        assertThat(summary.approvals().byType()).containsOnlyKeys("TRANSFER", "EXCHANGE");
        assertThat(summary.approvals().total()).isZero();
        assertThat(summary.procurement()).isNull();
    }

    @Test
    @DisplayName("a branch the caller is not assigned to is refused")
    void foreignBranchIsForbidden() {
        TestSecurity.authenticateAs(Set.of("SALE_VIEW"), Set.of(UUID.randomUUID()));
        assertThatThrownBy(() -> dashboardService.summary(world.branchId(), false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("a multi-branch user without a primary branch must name the branch")
    void ambiguousBranchIsRejected() {
        TestSecurity.authenticateAs(Set.of("SALE_VIEW"), Set.of(world.branchId(), UUID.randomUUID()));
        assertThatThrownBy(() -> dashboardService.summary(null, false))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("a super administrator without a branch sees the whole business")
    void superAdminAggregatesAcrossBranches() {
        TestSecurity.authenticateAsSuperAdmin();
        DashboardSummaryResponse summary = dashboardService.summary(null, false);
        assertThat(summary.branchId()).isNull();
        assertThat(summary.branchName()).isNull();
        assertThat(summary.notifications()).isNotNull();
    }

    @Test
    @DisplayName("repeat calls within 30 seconds are served from cache unless refresh is asked for")
    void cacheIsPerUserAndBypassable() {
        TestSecurity.authenticateAs(Set.of("INVENTORY_VIEW"), Set.of(world.branchId()));
        DashboardSummaryResponse first = dashboardService.summary(world.branchId(), false);
        DashboardSummaryResponse second = dashboardService.summary(world.branchId(), false);
        assertThat(second).isSameAs(first);

        DashboardSummaryResponse refreshed = dashboardService.summary(world.branchId(), true);
        assertThat(refreshed).isNotSameAs(first);
    }

    private void confirmedSale(BigDecimal total) {
        Sale sale = new Sale();
        sale.setSaleNumber("DSH-" + UUID.randomUUID().toString().substring(0, 12));
        sale.setCustomerId(world.customerId());
        sale.setBranchId(world.branchId());
        sale.setLocationId(world.locationId());
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setSaleDate(LocalDate.now());
        sale.setSubTotal(total);
        sale.setTotalAmount(total);
        sale.setPaidAmount(total);
        saleRepository.save(sale);
    }
}
