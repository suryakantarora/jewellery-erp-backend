package com.finotech.jewellery.modules.storefront.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class OtpSenderConfig {

    @Bean
    @ConditionalOnMissingBean(OtpSender.class)
    OtpSender loggingOtpSender() {
        return (phone, code, brandName) ->
                log.info("[OTP] {} is the {} sign-in code for {} (no SMS gateway configured)",
                        code, brandName, phone);
    }
}
