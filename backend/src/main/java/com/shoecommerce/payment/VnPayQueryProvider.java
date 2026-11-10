package com.shoecommerce.payment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!test & !demo")
final class VnPayQueryProvider implements PaymentQueryProvider {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "querydr";

    private final String tmnCode;
    private final String apiUrl;
    private final String serverIp;
    private final VnPayCrypto crypto;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    VnPayQueryProvider(@Value("${payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${payment.vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl,
            @Value("${payment.vnpay.server-ip:127.0.0.1}") String serverIp,
            VnPayCrypto crypto, ObjectMapper json) {
        this(tmnCode, apiUrl, serverIp, crypto, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    VnPayQueryProvider(String tmnCode, String apiUrl, String serverIp,
            VnPayCrypto crypto, ObjectMapper json, HttpClient http) {
        this.tmnCode = tmnCode.trim(); this.apiUrl = apiUrl.trim(); this.serverIp = serverIp.trim();
        this.crypto = crypto; this.json = json; this.http = http;
    }

    @Override
    public Result query(Request request) {
        try {
            Map<String, String> payload = signedRequest(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PaymentProviderUnavailableException("VNPAY QueryDr HTTP response is unavailable");
            }
            return verifiedResult(response.body(), request);
        } catch (PaymentProviderUnavailableException | PaymentReconciliationResultMismatchException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderUnavailableException("VNPAY QueryDr was interrupted");
        } catch (IOException exception) {
            throw new PaymentProviderUnavailableException("VNPAY QueryDr transport failed");
        } catch (Exception exception) {
            throw new PaymentReconciliationResultMismatchException("VNPAY QueryDr response could not be verified");
        }
    }

    Map<String, String> signedRequest(Request request) {
        requireConfiguration();
        if (request == null || request.requestReference() == null
                || !request.requestReference().matches("[A-Za-z0-9]{1,32}")
                || request.originalMerchantReference() == null || request.originalMerchantReference().isBlank()
                || request.originalTransactionAt() == null || request.requestedAt() == null
                || request.amountVnd() <= 0) {
            throw new IllegalArgumentException("VNPAY QueryDr request is incomplete");
        }
        Math.multiplyExact(request.amountVnd(), 100L);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", request.requestReference());
        fields.put("vnp_Version", VERSION);
        fields.put("vnp_Command", COMMAND);
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_TxnRef", request.originalMerchantReference());
        if (request.originalProviderTransactionNo() != null && !request.originalProviderTransactionNo().isBlank()) {
            fields.put("vnp_TransactionNo", request.originalProviderTransactionNo());
        }
        fields.put("vnp_TransactionDate", format(request.originalTransactionAt()));
        fields.put("vnp_CreateDate", format(request.requestedAt()));
        fields.put("vnp_IpAddr", serverIp);
        fields.put("vnp_OrderInfo", "Query " + request.originalMerchantReference());
        fields.put("vnp_SecureHash", crypto.hmac(requestChecksum(fields)));
        return fields;
    }

    Result verifiedResult(String body, Request request) throws Exception {
        JsonNode root = json.readTree(body);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : new String[] {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
                "vnp_PromotionCode", "vnp_PromotionAmount"}) {
            JsonNode value = root == null ? null : root.get(key);
            fields.put(key, value == null || value.isNull() ? "" : value.asString());
        }
        String supplied = root == null || root.get("vnp_SecureHash") == null ? null
                : root.get("vnp_SecureHash").asString();
        if (!crypto.verifies(responseChecksum(fields), supplied)) {
            throw new PaymentReconciliationResultMismatchException("VNPAY QueryDr response could not be verified");
        }
        long expectedAmount;
        long providerAmount;
        try {
            expectedAmount = Math.multiplyExact(request.amountVnd(), 100L);
            providerAmount = Long.parseLong(fields.get("vnp_Amount"));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new PaymentReconciliationResultMismatchException("VNPAY QueryDr response does not match the payment attempt");
        }
        if (!tmnCode.equals(fields.get("vnp_TmnCode"))
                || !COMMAND.equals(fields.get("vnp_Command"))
                || !request.originalMerchantReference().equals(fields.get("vnp_TxnRef"))
                || (request.originalProviderTransactionNo() != null
                        && !request.originalProviderTransactionNo().isBlank()
                        && !request.originalProviderTransactionNo().equals(fields.get("vnp_TransactionNo")))
                || expectedAmount != providerAmount
                || !"01".equals(fields.get("vnp_TransactionType"))) {
            throw new PaymentReconciliationResultMismatchException("VNPAY QueryDr response does not match the payment attempt");
        }
        String responseCode = fields.get("vnp_ResponseCode");
        String status = fields.get("vnp_TransactionStatus");
        String transactionNo = fields.get("vnp_TransactionNo");
        if (!responseCode.matches("\\d{2}") || !status.matches("\\d{2}")
                || !transactionNo.matches("\\d{1,32}")) {
            throw new PaymentReconciliationResultMismatchException("VNPAY QueryDr response could not be verified");
        }
        return new Result(classify(responseCode, status), fields.get("vnp_ResponseId"), responseCode, status,
                transactionNo, parsePaymentTime(fields.get("vnp_PayDate")),
                VnPayCrypto.sha256(responseChecksum(fields)));
    }

    static Outcome classify(String responseCode, String status) {
        if (!"00".equals(responseCode)) return Outcome.UNRESOLVED;
        return switch (status) {
            case "00" -> Outcome.CONFIRMED_PAID;
            case "02" -> Outcome.CONFIRMED_FAILED;
            case "01", "05", "06", "09" -> Outcome.UNRESOLVED;
            default -> Outcome.REVIEW_REQUIRED;
        };
    }

    static String requestChecksum(Map<String, String> f) {
        return String.join("|", f.get("vnp_RequestId"), f.get("vnp_Version"), f.get("vnp_Command"),
                f.get("vnp_TmnCode"), f.get("vnp_TxnRef"), f.get("vnp_TransactionDate"),
                f.get("vnp_CreateDate"), f.get("vnp_IpAddr"), f.get("vnp_OrderInfo"));
    }

    static String responseChecksum(Map<String, String> f) {
        return String.join("|", f.get("vnp_ResponseId"), f.get("vnp_Command"), f.get("vnp_ResponseCode"),
                f.get("vnp_Message"), f.get("vnp_TmnCode"), f.get("vnp_TxnRef"), f.get("vnp_Amount"),
                f.get("vnp_BankCode"), f.get("vnp_PayDate"), f.get("vnp_TransactionNo"),
                f.get("vnp_TransactionType"), f.get("vnp_TransactionStatus"), f.get("vnp_OrderInfo"),
                f.get("vnp_PromotionCode"), f.get("vnp_PromotionAmount"));
    }

    private void requireConfiguration() {
        if (tmnCode.isEmpty() || apiUrl.isEmpty() || serverIp.isEmpty()) {
            throw new PaymentProviderUnavailableException("VNPAY QueryDr is not configured");
        }
    }
    private static String format(Instant value) { return DATE_TIME.format(value.atZone(VnPayPaymentProvider.VNPAY_ZONE)); }
    private static Instant parsePaymentTime(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value, DATE_TIME).atZone(VnPayPaymentProvider.VNPAY_ZONE).toInstant(); }
        catch (DateTimeParseException exception) {
            throw new PaymentReconciliationResultMismatchException("VNPAY QueryDr response could not be verified");
        }
    }
}
