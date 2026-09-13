package com.finotech.jewellery.modules.sales.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lapses discount requests whose approval window has closed. Separate from
 * the service so the scheduled call goes through the transactional proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountRequestExpiryScheduler {

    private final DiscountRequestService discountRequestService;

    @Scheduled(fixedDelayString = "${jewellery.sales.discount-expiry-interval-ms:600000}")
    public void expire() {
        int expired = discountRequestService.expireOverdue();
        if (expired > 0) {
            log.info("Expired {} discount request(s)", expired);
        }
    }
}
