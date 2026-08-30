package com.shoecommerce.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
final class VnPayPaymentProvider implements PaymentProvider {
    static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long MAX_PROVIDER_AMOUNT = 999_999_999_999L;

    private final String tmnCode;
    private final VnPayCrypto crypto;
    private final String payUrl;
    private final String returnUrl;

    @Autowired
    VnPayPaymentProvider(
            @Value("${payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${payment.vnpay.return-url:}") String returnUrl,
            VnPayCrypto crypto) {
        this.tmnCode = tmnCode.trim();
        this.payUrl = payUrl.trim();
        this.returnUrl = returnUrl.trim();
        this.crypto = crypto;
    }

    VnPayPaymentProvider(String tmnCode, String hashSecret, String payUrl, String returnUrl) {
        this(tmnCode, payUrl, returnUrl, new VnPayCrypto(hashSecret));
    }

    @Override
    public String paymentUrl(Request request) {
        requireConfiguration();
        long providerAmount;
        try {
            providerAmount = Math.multiplyExact(request.amountVnd(), 100L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment amount exceeds VNPAY range", exception);
        }
        if (request.amountVnd() <= 0 || providerAmount > MAX_PROVIDER_AMOUNT) {
            throw new IllegalArgumentException("Payment amount exceeds VNPAY range");
        }
        if (!request.expiresAt().isAfter(request.createdAt())) {
            throw new IllegalArgumentException("VNPAY payment deadline has passed");
        }

        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_Amount", Long.toString(providerAmount));
        fields.put("vnp_Command", "pay");
        fields.put("vnp_CreateDate", format(request.createdAt()));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_ExpireDate", format(request.expiresAt()));
        fields.put("vnp_IpAddr", request.clientIp());
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_OrderInfo", "Thanh toan " + request.merchantReference());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_ReturnUrl", returnUrl);
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_TxnRef", request.merchantReference());
        fields.put("vnp_Version", "2.1.0");
        String canonical = canonical(fields);
        return payUrl + (payUrl.contains("?") ? "&" : "?") + canonical + "&vnp_SecureHash=" + crypto.hmac(canonical);
    }

    @Override
    public VerifiedResult verify(Map<String, String> parameters) {
        requireConfiguration();
        bound(parameters);
        String supplied = parameters.get("vnp_SecureHash");
        Map<String, String> signed = new TreeMap<>();
        parameters.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)
                    && value != null && !value.isEmpty()) {
                signed.put(key, value);
            }
        });
        String canonical = canonical(signed);
        if (!crypto.verifies(canonical, supplied)) {
            throw new InvalidSignatureException();
        }

        if (!tmnCode.equals(required(parameters, "vnp_TmnCode"))) throw new MerchantMismatchException();
        String currency = parameters.get("vnp_CurrCode");
        if (currency != null && !"VND".equals(currency)) throw new InvalidProviderDataException("Invalid VNPAY currency");
        String reference = required(parameters, "vnp_TxnRef");
        if (reference.length() > 100) throw new InvalidProviderDataException("Invalid VNPAY transaction reference");
        long providerAmount = parseDigits(required(parameters, "vnp_Amount"), 12, "amount");
        if (providerAmount % 100 != 0) throw new InvalidProviderDataException("Invalid VNPAY amount precision");
        String response = code(parameters, "vnp_ResponseCode");
        String status = code(parameters, "vnp_TransactionStatus");
        String transactionNo = optionalDigits(parameters.get("vnp_TransactionNo"), 32, "transaction number");
        if ("00".equals(response) && "00".equals(status) && transactionNo == null) {
            throw new InvalidProviderDataException("Successful VNPAY result has no transaction number");
        }
        return new VerifiedResult(reference, providerAmount / 100, transactionNo, response, status,
                parsePaymentTime(parameters.get("vnp_PayDate")), VnPayCrypto.sha256(canonical));
    }

    static String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private void requireConfiguration() {
        if (tmnCode.isEmpty() || payUrl.isEmpty() || returnUrl.isEmpty()) {
            throw new IllegalStateException("VNPAY is not configured. Set VNPAY_TMN_CODE, VNPAY_HASH_SECRET, VNPAY_PAY_URL and VNPAY_RETURN_URL.");
        }
    }
    private static void bound(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty() || parameters.size() > 32) {
            throw new InvalidProviderDataException("Invalid VNPAY parameter count");
        }
        int total = 0;
        for (var entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() > 64 || entry.getValue() == null || entry.getValue().length() > 512) {
                throw new InvalidProviderDataException("Invalid VNPAY parameter length");
            }
            total += entry.getKey().length() + entry.getValue().length();
        }
        if (total > 8_192) throw new InvalidProviderDataException("VNPAY parameters are too large");
    }
    private static String required(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) throw new InvalidProviderDataException("Missing " + key);
        return value;
    }
    private static String code(Map<String, String> parameters, String key) {
        String value = required(parameters, key);
        if (!value.matches("\\d{2}")) throw new InvalidProviderDataException("Invalid " + key);
        return value;
    }
    private static long parseDigits(String value, int maxLength, String label) {
        if (value.length() > maxLength || !value.matches("\\d+")) throw new InvalidProviderDataException("Invalid VNPAY " + label);
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw new InvalidProviderDataException("Invalid VNPAY " + label); }
    }
    private static String optionalDigits(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || "0".equals(value)) return null;
        parseDigits(value, maxLength, label);
        return value;
    }
    private static Instant parsePaymentTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value, DATE_TIME).atZone(VNPAY_ZONE).toInstant(); }
        catch (DateTimeParseException exception) { throw new InvalidProviderDataException("Invalid VNPAY payment time"); }
    }
    private static String format(Instant instant) { return DATE_TIME.format(instant.atZone(VNPAY_ZONE)); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    static class InvalidSignatureException extends RuntimeException { InvalidSignatureException() { super("Invalid VNPAY signature"); } }
    static class MerchantMismatchException extends RuntimeException { MerchantMismatchException() { super("Invalid VNPAY merchant code"); } }
    static class InvalidProviderDataException extends RuntimeException {
        InvalidProviderDataException(String message) { super(message); }
    }
}
