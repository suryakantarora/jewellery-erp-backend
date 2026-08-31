package com.finotech.jewellery.modules.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.modules.payment.api.request.RecordPaymentRequest;
import com.finotech.jewellery.modules.payment.api.request.RefundRequest;
import com.finotech.jewellery.modules.payment.api.response.PaymentResponse;
import com.finotech.jewellery.modules.payment.application.service.PaymentService;
import com.finotech.jewellery.modules.payment.domain.enums.PaymentMethod;
import com.finotech.jewellery.modules.sales.api.request.CreateSaleRequest;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.service.SaleService;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Duplicate payment prevention and the settlement rule that a sale's items only
 * become SOLD once the money is fully collected (section 12).
 */
@Import(PaymentIdempotencyIntegrationTest.Fixtures.class)
class PaymentIdempotencyIntegrationTest extends IntegrationTestBase {

    @TestConfiguration
    static class Fixtures {
        @Bean
        CommerceFixture commerceFixture(
                com.finotech.jewellery.modules.organization.application.service.OrganizationService o,
                com.finotech.jewellery.modules.metal.application.service.MetalService m,
                com.finotech.jewellery.modules.metal.application.service.MetalRateService r,
                com.finotech.jewellery.modules.product.application.service.ProductService p,
                com.finotech.jewellery.modules.product.application.service.ProductMasterService pm,
                com.finotech.jewellery.modules.pricing.application.service.PricingConfigService pc,
                com.finotech.jewellery.modules.customer.application.service.CustomerService c,
                com.finotech.jewellery.modules.inventory.application.service.JewelleryItemService i) {
            return new CommerceFixture(o, m, r, p, pm, pc, c, i);
        }
    }

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Autowired private CommerceFixture fixture;
    @Autowired private SaleService saleService;
    @Autowired private PaymentService paymentService;
    @Autowired private JewelleryItemService itemService;

    @Test
    @DisplayName("a replayed idempotency key does not take the money twice")
    void duplicatePaymentIsRejected() {
        Context context = openSale();
        String key = "PAY-" + UUID.randomUUID();

        RecordPaymentRequest request = new RecordPaymentRequest(context.saleId(),
                PaymentMethod.CASH, new BigDecimal("1000000"), null, null, null, null);

        PaymentResponse first = paymentService.record(request, key);
        PaymentResponse replay = paymentService.record(request, key);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.paymentNumber()).isEqualTo(first.paymentNumber());

        // Only one payment exists, and the sale was charged once.
        List<PaymentResponse> payments = paymentService.forSale(context.saleId());
        assertThat(payments).hasSize(1);
        assertThat(saleService.get(context.saleId()).paidAmount())
                .isEqualByComparingTo("1000000.00");
    }

    @Test
    @DisplayName("items are only marked SOLD once the sale is fully paid")
    void itemsLeaveStockOnlyOnFullSettlement() {
        Context context = openSale();
        BigDecimal total = saleService.get(context.saleId()).totalAmount();

        // Reserved while awaiting payment: held, but not yet sold.
        assertThat(itemService.get(context.itemId()).status()).isEqualTo(ItemStatus.RESERVED);

        BigDecimal part = total.subtract(new BigDecimal("1000000"));
        paymentService.record(new RecordPaymentRequest(context.saleId(), PaymentMethod.CASH,
                part, null, null, null, null), "p1-" + UUID.randomUUID());

        assertThat(saleService.get(context.saleId()).status()).isEqualTo(SaleStatus.PENDING_PAYMENT);
        assertThat(itemService.get(context.itemId()).status()).isEqualTo(ItemStatus.RESERVED);

        // Settling the balance confirms the sale and releases the goods.
        paymentService.record(new RecordPaymentRequest(context.saleId(), PaymentMethod.CARD,
                new BigDecimal("1000000"), null, "4242", null, null), "p2-" + UUID.randomUUID());

        SaleResponse settled = saleService.get(context.saleId());
        assertThat(settled.status()).isEqualTo(SaleStatus.CONFIRMED);
        assertThat(settled.invoiceNumber()).isNotBlank();
        assertThat(settled.outstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(itemService.get(context.itemId()).status()).isEqualTo(ItemStatus.SOLD);
    }

    @Test
    @DisplayName("a payment cannot exceed the outstanding balance")
    void overpaymentIsRejected() {
        Context context = openSale();
        BigDecimal total = saleService.get(context.saleId()).totalAmount();

        assertThatThrownBy(() -> paymentService.record(new RecordPaymentRequest(context.saleId(),
                PaymentMethod.CASH, total.add(new BigDecimal("1")), null, null, null, null),
                "over-" + UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds the outstanding balance");
    }

    @Test
    @DisplayName("a fully paid sale takes no further payment")
    void settledSaleRejectsFurtherPayment() {
        Context context = openSale();
        BigDecimal total = saleService.get(context.saleId()).totalAmount();
        paymentService.record(new RecordPaymentRequest(context.saleId(), PaymentMethod.CASH,
                total, null, null, null, null), "full-" + UUID.randomUUID());

        // Nothing is outstanding, so the payment is refused on the balance.
        assertThatThrownBy(() -> paymentService.record(new RecordPaymentRequest(context.saleId(),
                PaymentMethod.CASH, new BigDecimal("1000"), null, null, null, null),
                "extra-" + UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds the outstanding balance");
    }

    @Test
    @DisplayName("a cancelled sale takes no payment at all")
    void cancelledSaleRejectsPayment() {
        Context context = openSale();
        saleService.cancel(context.saleId(), "Abandoned at the counter");

        assertThatThrownBy(() -> paymentService.record(new RecordPaymentRequest(context.saleId(),
                PaymentMethod.CASH, new BigDecimal("1000"), null, null, null, null),
                "cancelled-" + UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer accepts payment");
    }

    @Test
    @DisplayName("a refund cannot exceed what was captured")
    void overRefundIsRejected() {
        Context context = openSale();
        BigDecimal total = saleService.get(context.saleId()).totalAmount();
        PaymentResponse payment = paymentService.record(new RecordPaymentRequest(context.saleId(),
                PaymentMethod.CASH, total, null, null, null, null), "r-" + UUID.randomUUID());

        paymentService.refund(new RefundRequest(payment.id(), total, "Returned"),
                "refund-" + UUID.randomUUID());

        assertThatThrownBy(() -> paymentService.refund(
                new RefundRequest(payment.id(), new BigDecimal("1"), "Again"),
                "refund2-" + UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds the remaining refundable amount");
    }

    @Test
    @DisplayName("cancelling an unpaid sale releases its items back to stock")
    void cancellingReleasesItems() {
        Context context = openSale();
        saleService.cancel(context.saleId(), "Customer changed their mind");

        assertThat(saleService.get(context.saleId()).status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(itemService.get(context.itemId()).status()).isEqualTo(ItemStatus.AVAILABLE);
    }

    // ---------- helpers ----------

    private record Context(UUID saleId, UUID itemId, CommerceFixture.World world) {
    }

    private Context openSale() {
        CommerceFixture.World world = fixture.create(new BigDecimal("1850000"),
                new BigDecimal("45000"), new BigDecimal("8.0"), new BigDecimal("10.0"));
        UUID itemId = fixture.availableItem(world, new BigDecimal("12.500"));

        SaleResponse sale = saleService.create(new CreateSaleRequest(
                world.customerId(), world.branchId(), world.locationId(), null, null, null, null,
                null, List.of(new CreateSaleRequest.Line(itemId, null, null))), null);
        return new Context(sale.id(), itemId, world);
    }
}
