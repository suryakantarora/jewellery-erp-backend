package com.finotech.jewellery.modules.storefront.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Customer app settings bound from {@code jewellery.storefront.*}.
 *
 * @param customerTokenDays      lifetime of a customer token; the app has no
 *                               refresh flow, so this is how long an OTP lasts
 * @param catalogueCacheSeconds  how long a priced catalogue is reused. Pricing
 *                               every piece on every request is the cost this
 *                               bounds; a rate change shows within this window
 * @param bootstrapTenantKey     development convenience: when set and the
 *                               platform has exactly one company with no tenant
 *                               row, that company answers to this key
 */
@ConfigurationProperties(prefix = "jewellery.storefront")
public record StorefrontProperties(Otp otp, Integer customerTokenDays,
                                   Integer catalogueCacheSeconds, String bootstrapTenantKey) {

    public StorefrontProperties {
        otp = otp == null ? new Otp(null, null, null, null, null) : otp;
        customerTokenDays = customerTokenDays == null ? 90 : customerTokenDays;
        catalogueCacheSeconds = catalogueCacheSeconds == null ? 60 : catalogueCacheSeconds;
    }

    /**
     * @param fixedCode when set, every challenge uses this code and nothing is
     *                  sent. For development and store review accounts only.
     */
    public record Otp(Integer length, Integer ttlSeconds, Integer resendSeconds,
                      Integer maxAttempts, String fixedCode) {

        public Otp {
            length = length == null ? 6 : length;
            ttlSeconds = ttlSeconds == null ? 300 : ttlSeconds;
            resendSeconds = resendSeconds == null ? 30 : resendSeconds;
            maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        }
    }
}
