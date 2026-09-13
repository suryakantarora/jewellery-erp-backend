package com.finotech.jewellery.modules.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.CommerceFixture;
import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.exchange.api.request.ExchangeRequests;
import com.finotech.jewellery.modules.exchange.api.response.ExchangeResponse;
import com.finotech.jewellery.modules.exchange.application.service.ExchangeService;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/** A retried intake POST must not take in the same piece twice. */
@Import(CommerceFixture.class)
class ExchangeIdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired private CommerceFixture fixture;
    @Autowired private ExchangeService exchangeService;

    private CommerceFixture.World world;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAsSuperAdmin();
        world = fixture.create(new BigDecimal("2000000"), new BigDecimal("50000"),
                new BigDecimal("2.0"), null);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("replaying X-Idempotency-Key returns the intake already created")
    void replayReturnsSameIntake() {
        String key = "EX-" + UUID.randomUUID();
        ExchangeRequests.ReceiveRequest request = new ExchangeRequests.ReceiveRequest(
                ExchangeType.BUYBACK, world.customerId(), world.branchId(), world.locationId(),
                world.metalId(), world.purityId(), null, "Old bangle", 1, null);

        ExchangeResponse first = exchangeService.receive(request, key);
        ExchangeResponse replay = exchangeService.receive(request, key);

        assertThat(first.status()).isEqualTo(ExchangeStatus.RECEIVED);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.referenceNumber()).isEqualTo(first.referenceNumber());

        long forCustomer = exchangeService.search(null, null, world.customerId(), null,
                PageRequest.of(0, 10)).totalElements();
        assertThat(forCustomer).isEqualTo(1);

        // A different key is a genuinely new intake.
        ExchangeResponse second = exchangeService.receive(request, "EX-" + UUID.randomUUID());
        assertThat(second.id()).isNotEqualTo(first.id());
    }
}
