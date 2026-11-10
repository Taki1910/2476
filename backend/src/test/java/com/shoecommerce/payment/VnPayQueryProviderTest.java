package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class VnPayQueryProviderTest {
    private final VnPayCrypto crypto = new VnPayCrypto("fixture-secret");
    private final ObjectMapper json = JsonMapper.builder().build();
    private final VnPayQueryProvider provider = new VnPayQueryProvider("TESTCODE", "https://provider.test/query",
            "127.0.0.1", crypto, json, null);
    private final PaymentQueryProvider.Request request = new PaymentQueryProvider.Request("REQ123", "PAY123",
            "987654321", Instant.parse("2026-08-27T02:05:00Z"), 125_000,
            Instant.parse("2026-08-27T03:00:00Z"));

    @Test
    void signsOfficialQueryDrFieldsWithOriginalReferenceAndTransactionDate() {
        Map<String, String> fields = provider.signedRequest(request);

        assertThat(fields).containsEntry("vnp_Command", "querydr")
                .containsEntry("vnp_TxnRef", "PAY123")
                .containsEntry("vnp_TransactionNo", "987654321")
                .containsEntry("vnp_TransactionDate", "20260827090500")
                .containsEntry("vnp_CreateDate", "20260827100000")
                .containsEntry("vnp_OrderInfo", "Query PAY123")
                .containsEntry("vnp_SecureHash", "5db3c7e05e30ab5fdf8a36465b37796af9554116419cfac262d728853dc46153142efbd82623d48ffc459888b1c12f69344da0b3d89dbc600b1dd1b70bb26f72");
        assertThat(VnPayQueryProvider.requestChecksum(fields)).isEqualTo(
                "REQ123|2.1.0|querydr|TESTCODE|PAY123|20260827090500|20260827100000|127.0.0.1|Query PAY123");
    }

    @Test
    void distinguishesQueryAcceptanceFromPaymentStatus() throws Exception {
        assertThat(VnPayQueryProvider.classify("00", "00")).isEqualTo(PaymentQueryProvider.Outcome.CONFIRMED_PAID);
        assertThat(VnPayQueryProvider.classify("00", "02")).isEqualTo(PaymentQueryProvider.Outcome.CONFIRMED_FAILED);
        assertThat(VnPayQueryProvider.classify("00", "01")).isEqualTo(PaymentQueryProvider.Outcome.UNRESOLVED);
        assertThat(VnPayQueryProvider.classify("00", "04")).isEqualTo(PaymentQueryProvider.Outcome.REVIEW_REQUIRED);
        assertThat(VnPayQueryProvider.classify("91", "00")).isEqualTo(PaymentQueryProvider.Outcome.UNRESOLVED);

        Map<String, String> response = response("00", "00");
        response.put("vnp_SecureHash", crypto.hmac(VnPayQueryProvider.responseChecksum(response)));
        PaymentQueryProvider.Result result = provider.verifiedResult(json.writeValueAsString(response), request);
        assertThat(result.outcome()).isEqualTo(PaymentQueryProvider.Outcome.CONFIRMED_PAID);
        assertThat(result.providerPaidAt()).isEqualTo(Instant.parse("2026-08-27T03:10:00Z"));
    }

    @Test
    void rejectsInvalidSignatureBeforeMismatchedProviderEvidence() throws Exception {
        Map<String, String> response = response("00", "00");
        response.put("vnp_Amount", "12499900");
        response.put("vnp_SecureHash", "00".repeat(64));

        assertThatThrownBy(() -> provider.verifiedResult(json.writeValueAsString(response), request))
                .isInstanceOf(PaymentReconciliationResultMismatchException.class)
                .hasMessage("VNPAY QueryDr response could not be verified");
    }

    @Test
    void rejectsSignedAmountOrReferenceMismatchWithoutRounding() throws Exception {
        Map<String, String> response = response("00", "00");
        response.put("vnp_Amount", "12499900");
        response.put("vnp_TxnRef", "OTHER");
        response.put("vnp_SecureHash", crypto.hmac(VnPayQueryProvider.responseChecksum(response)));

        assertThatThrownBy(() -> provider.verifiedResult(json.writeValueAsString(response), request))
                .isInstanceOf(PaymentReconciliationResultMismatchException.class)
                .hasMessage("VNPAY QueryDr response does not match the payment attempt");
    }

    @Test
    void rejectsSignedProviderTransactionMismatchWhenOriginalTransactionIsKnown() throws Exception {
        Map<String, String> response = response("00", "00");
        response.put("vnp_TransactionNo", "11223344");
        response.put("vnp_SecureHash", crypto.hmac(VnPayQueryProvider.responseChecksum(response)));

        assertThatThrownBy(() -> provider.verifiedResult(json.writeValueAsString(response), request))
                .isInstanceOf(PaymentReconciliationResultMismatchException.class)
                .hasMessage("VNPAY QueryDr response does not match the payment attempt");
    }

    @Test
    void rejectsSignedResponseWithMissingStatusOrInvalidProviderTransactionNumber() throws Exception {
        PaymentQueryProvider.Request withoutKnownTransaction = new PaymentQueryProvider.Request(
                "REQ124", "PAY123", null, request.originalTransactionAt(), request.amountVnd(), request.requestedAt());
        Map<String, String> missingStatus = response("00", "");
        missingStatus.put("vnp_SecureHash", crypto.hmac(VnPayQueryProvider.responseChecksum(missingStatus)));
        Map<String, String> invalidTransaction = response("00", "00");
        invalidTransaction.put("vnp_TransactionNo", "not-a-number");
        invalidTransaction.put("vnp_SecureHash", crypto.hmac(VnPayQueryProvider.responseChecksum(invalidTransaction)));

        assertThatThrownBy(() -> provider.verifiedResult(
                json.writeValueAsString(missingStatus), withoutKnownTransaction))
                .isInstanceOf(PaymentReconciliationResultMismatchException.class)
                .hasMessage("VNPAY QueryDr response could not be verified");
        assertThatThrownBy(() -> provider.verifiedResult(
                json.writeValueAsString(invalidTransaction), withoutKnownTransaction))
                .isInstanceOf(PaymentReconciliationResultMismatchException.class)
                .hasMessage("VNPAY QueryDr response could not be verified");
    }

    private static Map<String, String> response(String responseCode, String status) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "RESP123"); fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", responseCode); fields.put("vnp_Message", "accepted");
        fields.put("vnp_TmnCode", "TESTCODE"); fields.put("vnp_TxnRef", "PAY123");
        fields.put("vnp_Amount", "12500000"); fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20260827101000"); fields.put("vnp_TransactionNo", "987654321");
        fields.put("vnp_TransactionType", "01"); fields.put("vnp_TransactionStatus", status);
        fields.put("vnp_OrderInfo", "Query PAY123"); fields.put("vnp_PromotionCode", "");
        fields.put("vnp_PromotionAmount", "");
        return fields;
    }
}
