package com.finotech.jewellery.modules.storefront.application;

/**
 * Delivers a one-time code to a phone. The default writes it to the log;
 * register a bean of this type for the SMS gateway the deployment uses and the
 * default steps aside.
 */
public interface OtpSender {

    void send(String phone, String code, String brandName);
}
