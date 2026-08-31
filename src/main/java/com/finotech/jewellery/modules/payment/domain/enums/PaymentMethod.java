package com.finotech.jewellery.modules.payment.domain.enums;

public enum PaymentMethod {
    CASH,
    CARD,
    BANK_TRANSFER,
    QR_PAYMENT,
    GIFT_VOUCHER,
    STORE_CREDIT,
    /** Value of old jewellery applied against a purchase. */
    EXCHANGE_CREDIT
}
