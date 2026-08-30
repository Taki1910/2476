package com.shoecommerce.payment;

import java.time.Instant;
import java.util.Map;

interface PaymentProvider {
    String paymentUrl(Request request);
    VerifiedResult verify(Map<String, String> parameters);

    record Request(String merchantReference, long amountVnd, String clientIp, Instant createdAt, Instant expiresAt) { }
    record VerifiedResult(String merchantReference, long amountVnd, String providerTransactionNo,
            String responseCode, String transactionStatus, Instant providerPaidAt, String evidenceHash) {
        boolean successful() { return "00".equals(responseCode) && "00".equals(transactionStatus); }
    }
}
