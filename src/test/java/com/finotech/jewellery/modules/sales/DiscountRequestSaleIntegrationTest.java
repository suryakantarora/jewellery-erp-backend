package com.finotech.jewellery.modules.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.pricing.domain.enums.DiscountType;
import com.finotech.jewellery.modules.sales.api.request.CreateSaleRequest;
import com.finotech.jewellery.modules.sales.api.request.DiscountRequestRequests.CreateDiscountRequest;
import com.finotech.jewellery.modules.sales.api.response.DiscountRequestResponse;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.service.DiscountRequestService;
import com.finotech.jewellery.modules.sales.application.service.SaleService;
import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * A salesperson without DISCOUNT_APPROVE can only exceed the branch policy
 * (5% on their own, 20% with sign-off) by drawing on an approved request —
 * and only up to what was approved.
 */
@Import(CommerceFixture.class)
class DiscountRequestSaleIntegrationTest extends IntegrationTestBase {

    @Autowired private CommerceFixture fixture;
    @Autowired private DiscountRequestService discountRequestService;
    @Autowired private SaleService saleService;

    private CommerceFixture.World world;
    private UUID itemId;
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAsSuperAdmin();
        world = fixture.create(new BigDecimal("2000000"), new BigDecimal("50000"),
                new BigDecimal("2.0"), null);
        itemId = fixture.availableItem(world, new BigDecimal("10.000"));
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private void asSalesperson() {
        TestSecurity.authenticateAs(salespersonId, Set.of("DISCOUNT_REQUEST", "SALE_CREATE"),
                Set.of(world.branchId()));
    }

    private void asManager() {
        TestSecurity.authenticateAs(managerId, Set.of("DISCOUNT_APPROVE"), Set.of(world.branchId()));
    }

    private UUID approvedRequest(BigDecimal percentage) {
        asSalesperson();
        DiscountRequestResponse created = discountRequestService.create(new CreateDiscountRequest(
                world.branchId(), world.customerId(), itemId, null, percentage, null, null,
                "Loyal customer"));
        asManager();
        DiscountRequestResponse approved = discountRequestService.approve(created.id(), "OK once");
        assertThat(approved.status()).isEqualTo(DiscountRequestStatus.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo("test-user-" + managerId);
        return created.id();
    }

    @Test
    @DisplayName("a salesperson cannot approve their own request")
    void requesterCannotApprove() {
        asSalesperson();
        DiscountRequestResponse created = discountRequestService.create(new CreateDiscountRequest(
                world.branchId(), null, null, null, new BigDecimal("10"), null, null, "Please"));
        TestSecurity.authenticateAs(salespersonId, Set.of("DISCOUNT_APPROVE"), Set.of(world.branchId()));
        assertThatThrownBy(() -> discountRequestService.approve(created.id(), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("without approval a discount beyond the branch limit is refused")
    void overPolicyDiscountNeedsApproval() {
        asSalesperson();
        assertThatThrownBy(() -> saleService.create(sale(new BigDecimal("10"), null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requires approval");
    }

    @Test
    @DisplayName("an approved request authorises the sale and is consumed by it")
    void approvedRequestIsConsumedBySale() {
        UUID requestId = approvedRequest(new BigDecimal("10"));

        asSalesperson();
        SaleResponse sale = saleService.create(sale(new BigDecimal("10"), requestId), null);
        assertThat(sale.discountApprovedBy()).isEqualTo("test-user-" + managerId);
        assertThat(sale.discountTotal()).isGreaterThan(BigDecimal.ZERO);

        DiscountRequestResponse consumed = discountRequestService.get(requestId);
        assertThat(consumed.status()).isEqualTo(DiscountRequestStatus.CONSUMED);
        assertThat(consumed.consumedBySaleId()).isEqualTo(sale.id());

        // Spent: a second sale cannot draw on it.
        TestSecurity.authenticateAsSuperAdmin();
        UUID anotherItem = fixture.availableItem(world, new BigDecimal("6.000"));
        asSalesperson();
        assertThatThrownBy(() -> saleService.create(new CreateSaleRequest(world.customerId(),
                world.branchId(), world.locationId(), null, null, null, null, null,
                List.of(new CreateSaleRequest.Line(anotherItem, DiscountType.PERCENTAGE,
                        new BigDecimal("10"))), requestId), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("CONSUMED");
    }

    @Test
    @DisplayName("a discount above what was approved is rejected")
    void overLimitDiscountIsRejected() {
        UUID requestId = approvedRequest(new BigDecimal("10"));
        asSalesperson();
        assertThatThrownBy(() -> saleService.create(sale(new BigDecimal("15"), requestId), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds the approved");
        assertThat(discountRequestService.get(requestId).status())
                .isEqualTo(DiscountRequestStatus.APPROVED);
    }

    private CreateSaleRequest sale(BigDecimal percentage, UUID discountRequestId) {
        return new CreateSaleRequest(world.customerId(), world.branchId(), world.locationId(),
                null, null, null, null, null,
                List.of(new CreateSaleRequest.Line(itemId, DiscountType.PERCENTAGE, percentage)),
                discountRequestId);
    }
}
