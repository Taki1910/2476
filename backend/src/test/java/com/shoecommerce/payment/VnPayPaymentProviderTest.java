package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VnPayPaymentProviderTest {
    private static final String REQUEST_SIGNATURE =
            "ddebc6351791b8e92d005c1e32479ccb5e67b098cdb9c9c43c4e7262a87a7c2c0cc9f426f8d7bbdc5a2f148edc1f1fedc11f69c0cb1a7a28060cf4a9b7fbb914";
    private static final String CALLBACK_SIGNATURE =
            "034477fb4697c81abe3c44de9e04aa96ebf711baeef15d3c82c1126ff5e21e26ffff84c37edd9f8a5e72f7ed239436579b122aaaf03b97f2fa87b381e76c7df8";

    private final VnPayPaymentProvider provider = new VnPayPaymentProvider(
            "TESTCODE", "fixture-secret", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "https://merchant.test/api/v1/payments/vnpay/return");

    @Test
    void signsCanonicalV21RequestWithExactVndAmountAndGmt7Deadline() {
        String url = provider.paymentUrl(new PaymentProvider.Request("REF123", 125_000, "127.0.0.1",
                Instant.parse("2026-08-27T08:00:00Z"), Instant.parse("2026-08-27T08:10:00Z")));

        assertThat(url)
                .contains("vnp_Amount=12500000")
                .contains("vnp_CreateDate=20260827150000")
                .contains("vnp_ExpireDate=20260827151000")
                .endsWith("vnp_SecureHash=" + REQUEST_SIGNATURE);
    }

    @Test
    void verifiesKnownCallbackAndRejectsAnyModifiedSignedField() {
        Map<String, String> callback = successCallback();
        callback.put("vnp_SecureHash", CALLBACK_SIGNATURE);

        PaymentProvider.VerifiedResult result = provider.verify(callback);

        assertThat(result.amountVnd()).isEqualTo(125_000);
        assertThat(result.merchantReference()).isEqualTo("REF123");
        assertThat(result.providerTransactionNo()).isEqualTo("123456789");
        assertThat(result.providerPaidAt()).isEqualTo(Instant.parse("2026-08-27T08:05:00Z"));
        assertThat(result.successful()).isTrue();

        callback.put("vnp_Amount", "12500001");
        assertThatThrownBy(() -> provider.verify(callback))
                .isInstanceOf(VnPayPaymentProvider.InvalidSignatureException.class);
    }

    @Test
    void rejectsInvalidSignatureMalformedAmountsAndOverflow() {
        Map<String, String> invalid = successCallback();
        invalid.put("vnp_SecureHash", "00".repeat(64));
        assertThatThrownBy(() -> provider.verify(invalid))
                .isInstanceOf(VnPayPaymentProvider.InvalidSignatureException.class);

        assertThatThrownBy(() -> provider.paymentUrl(new PaymentProvider.Request("OVERFLOW", Long.MAX_VALUE,
                "127.0.0.1", Instant.parse("2026-08-27T08:00:00Z"), Instant.parse("2026-08-27T08:10:00Z"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("range");
    }

    private static Map<String, String> successCallback() {
        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("vnp_TxnRef", "REF123");
        callback.put("vnp_TransactionStatus", "00");
        callback.put("vnp_TmnCode", "TESTCODE");
        callback.put("vnp_Amount", "12500000");
        callback.put("vnp_ResponseCode", "00");
        callback.put("vnp_TransactionNo", "123456789");
        callback.put("vnp_PayDate", "20260827150500");
        callback.put("vnp_CurrCode", "VND");
        return callback;
    }
}
