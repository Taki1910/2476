package com.shoecommerce.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class VnPayCrypto {
    private final String secret;

    VnPayCrypto(@Value("${payment.vnpay.hash-secret:}") String secret) { this.secret = secret; }

    String hmac(String value) {
        if (secret.isEmpty()) throw new IllegalStateException("VNPAY hash secret is not configured");
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA512 is unavailable", exception);
        }
    }

    boolean verifies(String value, String supplied) {
        if (supplied == null) return false;
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(hmac(value)), HexFormat.of().parseHex(supplied));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
