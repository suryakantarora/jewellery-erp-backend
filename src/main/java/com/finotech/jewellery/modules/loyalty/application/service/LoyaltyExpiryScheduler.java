package com.finotech.jewellery.modules.loyalty.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires points that have passed their validity date.
 *
 * <p>Runs daily by default. Each award is expired exactly once, so a missed run
 * simply catches up on the next one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyExpiryScheduler {

    private final LoyaltyService loyaltyService;

    @Value("${jewellery.loyalty.expiry-batch-size:500}")
    private int batchSize;

    @Scheduled(cron = "${jewellery.loyalty.expiry-cron:0 30 2 * * *}")
    public void expirePoints() {
        int expired = loyaltyService.expireDuePoints(batchSize);
        if (expired > 0) {
            log.info("Loyalty expiry run processed {} award(s)", expired);
        }
    }
}
