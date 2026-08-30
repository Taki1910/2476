package com.shoecommerce.payment;

import java.time.Instant;

public interface VoidProvider {
    Result reverse(Request request);

    record Request(String requestReference, String originalMerchantReference,
            String originalProviderTransactionNo, Instant originalPaidAt,
            long amountVnd, Instant requestedAt) { }

    record Result(Outcome outcome, String responseId, String responseCode,
            String transactionStatus, String providerTransactionNo, String evidenceHash) { }

    enum Outcome { SUCCEEDED, DEFINITIVE_FAILED, UNKNOWN, REVIEW_REQUIRED }
}
