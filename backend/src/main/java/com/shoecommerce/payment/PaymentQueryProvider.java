package com.shoecommerce.payment;

import java.time.Instant;

public interface PaymentQueryProvider {
    Result query(Request request);

    record Request(String requestReference, String originalMerchantReference,
            String originalProviderTransactionNo, Instant originalTransactionAt,
            long amountVnd, Instant requestedAt) { }

    record Result(Outcome outcome, String responseId, String responseCode,
            String transactionStatus, String providerTransactionNo,
            Instant providerPaidAt, String evidenceHash) { }

    enum Outcome { CONFIRMED_PAID, CONFIRMED_FAILED, UNRESOLVED, REVIEW_REQUIRED }
}
