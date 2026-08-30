package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class VnPayVoidProviderTest {
    private final VnPayCrypto crypto = new VnPayCrypto("fixture-secret");
    private final ObjectMapper json = JsonMapper.builder().build();
    private final VnPayVoidProvider provider = new VnPayVoidProvider("TESTCODE", "https://provider.test/refund",
            "qa-user", "127.0.0.1", crypto, json, HttpClient.newHttpClient());
    private final VoidProvider.Request request = new VoidProvider.Request("REQ123", "PAY123", "987654321",
            Instant.parse("2026-08-27T02:05:00Z"), 125_000, Instant.parse("2026-08-27T03:00:00Z"));

    @Test
    void signsFullReversalWithExactVndTimesOneHundred() {
        Map<String, String> fields = provider.signedRequest(request);

        assertThat(fields).containsEntry("vnp_Command", "refund")
                .containsEntry("vnp_TransactionType", "02")
                .containsEntry("vnp_Amount", "12500000")
                .containsEntry("vnp_TxnRef", "PAY123")
                .containsEntry("vnp_TransactionNo", "987654321")
                .containsEntry("vnp_TransactionDate", "20260827090500");
        assertThat(fields.get("vnp_SecureHash")).isEqualTo(
                "1aabeadfea8c49b1c8faef98f848e167c7fe771a4371f2e8c0502991bbdb9157274cac0cdf49acb220f3d143d97ad8e726f94f2cc8c2b33abe28b6e14dc27558");
    }

    @Test
    void responseAcceptanceAndTransactionOutcomeAreDistinct() throws Exception {
        assertThat(VnPayVoidProvider.classify("00", "00")).isEqualTo(VoidProvider.Outcome.SUCCEEDED);
        assertThat(VnPayVoidProvider.classify("00", "05")).isEqualTo(VoidProvider.Outcome.UNKNOWN);
        assertThat(VnPayVoidProvider.classify("00", "02")).isEqualTo(VoidProvider.Outcome.DEFINITIVE_FAILED);
        assertThat(VnPayVoidProvider.classify("00", "07")).isEqualTo(VoidProvider.Outcome.REVIEW_REQUIRED);
        assertThat(VnPayVoidProvider.classify("94", "00")).isEqualTo(VoidProvider.Outcome.UNKNOWN);

        Map<String, String> response = response("00", "05");
        response.put("vnp_SecureHash", crypto.hmac(VnPayVoidProvider.responseChecksum(response)));
        VoidProvider.Result result = provider.verifiedResult(json.writeValueAsString(response), request);
        assertThat(result.outcome()).isEqualTo(VoidProvider.Outcome.UNKNOWN);
    }

    @Test
    void rejectsUnauthenticatedOrMismatchedEvidence() throws Exception {
        Map<String, String> response = response("00", "00");
        response.put("vnp_SecureHash", "00".repeat(64));
        assertThatThrownBy(() -> provider.verifiedResult(json.writeValueAsString(response), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("signature");

        response.put("vnp_Amount", "12499900");
        response.put("vnp_SecureHash", crypto.hmac(VnPayVoidProvider.responseChecksum(response)));
        assertThatThrownBy(() -> provider.verifiedResult(json.writeValueAsString(response), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("match");
    }

    private static Map<String, String> response(String responseCode, String status) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "RESP123"); fields.put("vnp_Command", "refund");
        fields.put("vnp_ResponseCode", responseCode); fields.put("vnp_Message", "accepted");
        fields.put("vnp_TmnCode", "TESTCODE"); fields.put("vnp_TxnRef", "PAY123");
        fields.put("vnp_Amount", "12500000"); fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20260827101000"); fields.put("vnp_TransactionNo", "11223344");
        fields.put("vnp_TransactionType", "02"); fields.put("vnp_TransactionStatus", status);
        fields.put("vnp_OrderInfo", "Void PAY123");
        return fields;
    }
}
