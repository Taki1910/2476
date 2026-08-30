package com.shoecommerce.payment;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class VnPayPaymentController {
    private static final Logger log = LoggerFactory.getLogger(VnPayPaymentController.class);
    private final PaymentProvider provider;
    private final VerifiedPaymentResultService results;
    private final PaymentAttemptService attempts;
    private final String frontendResultUrl;

    VnPayPaymentController(PaymentProvider provider, VerifiedPaymentResultService results,
            PaymentAttemptService attempts,
            @Value("${payment.vnpay.frontend-result-url:http://localhost:5173/payment/result}") String frontendResultUrl) {
        this.provider = provider; this.results = results; this.attempts = attempts;
        this.frontendResultUrl = frontendResultUrl;
    }

    @GetMapping("/api/v1/payments/vnpay/ipn")
    VnPayAcknowledgement ipn(@RequestParam MultiValueMap<String, String> raw) {
        try {
            PaymentProvider.VerifiedResult verified = provider.verify(singleValues(raw));
            return switch (results.apply(verified)) {
                case APPLIED -> new VnPayAcknowledgement("00", "Confirm Success");
                case ALREADY_PROCESSED -> new VnPayAcknowledgement("02", "Order already confirmed");
                case NOT_FOUND -> new VnPayAcknowledgement("01", "Order not found");
                case AMOUNT_MISMATCH -> new VnPayAcknowledgement("04", "Invalid amount");
            };
        } catch (VnPayPaymentProvider.InvalidSignatureException exception) {
            return new VnPayAcknowledgement("97", "Invalid signature");
        } catch (VnPayPaymentProvider.MerchantMismatchException
                | VnPayPaymentProvider.InvalidProviderDataException exception) {
            return new VnPayAcknowledgement("99", "Invalid provider data");
        } catch (RuntimeException exception) {
            log.error("VNPAY IPN processing failed", exception);
            return new VnPayAcknowledgement("99", "Internal error");
        }
    }

    @GetMapping("/api/v1/payments/vnpay/return")
    ResponseEntity<Void> customerReturn(@RequestParam MultiValueMap<String, String> raw) {
        String suffix = "?state=unverified";
        try {
            PaymentProvider.VerifiedResult verified = provider.verify(singleValues(raw));
            var attemptId = attempts.findAttemptIdForVerifiedReturn(verified.merchantReference());
            if (attemptId != null) suffix = "?attemptId=" + encode(attemptId.toString());
        } catch (RuntimeException ignored) {
            // Browser return is navigation only. IPN remains the sole payment authority.
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, URI.create(frontendResultUrl + suffix).toASCIIString())
                .build();
    }

    private static Map<String, String> singleValues(MultiValueMap<String, String> raw) {
        Map<String, String> values = new LinkedHashMap<>();
        raw.forEach((key, candidates) -> {
            if (candidates == null || candidates.size() != 1) {
                throw new VnPayPaymentProvider.InvalidProviderDataException("Duplicate VNPAY parameter");
            }
            values.put(key, candidates.getFirst());
        });
        return values;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    record VnPayAcknowledgement(String RspCode, String Message) { }
}
