package com.finotech.jewellery.shared.utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Human-readable reference numbers for documents (transfers, receipts, sales).
 */
public final class CodeGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeGenerator() {
    }

    /** e.g. {@code MOV-20260830-K7X4Q2}. */
    public static String reference(String prefix) {
        return prefix + "-" + DATE.format(Instant.now()) + "-" + random(6);
    }

    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
