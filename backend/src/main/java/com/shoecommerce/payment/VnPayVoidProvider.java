package com.shoecommerce.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!test & !demo")
final class VnPayVoidProvider implements VoidProvider {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "refund";
    private static final String FULL_REVERSAL = "02";

    private final String tmnCode;
    private final String apiUrl;
    private final String createBy;
    private final String serverIp;
    private final VnPayCrypto crypto;
    private final ObjectMapper json;
    private final HttpClient http;

    VnPayVoidProvider(@Value("${payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${payment.vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl,
            @Value("${payment.vnpay.refund-create-by:shoe-commerce}") String createBy,
            @Value("${payment.vnpay.server-ip:127.0.0.1}") String serverIp,
            VnPayCrypto crypto, ObjectMapper json) {
        this(tmnCode, apiUrl, createBy, serverIp, crypto, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    VnPayVoidProvider(String tmnCode, String apiUrl, String createBy, String serverIp,
            VnPayCrypto crypto, ObjectMapper json, HttpClient http) {
        this.tmnCode = tmnCode.trim(); this.apiUrl = apiUrl.trim(); this.createBy = createBy.trim();
        this.serverIp = serverIp.trim(); this.crypto = crypto; this.json = json; this.http = http;
    }

    @Override
    public Result reverse(Request request) {
        try {
            Map<String, String> payload = signedRequest(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("VNPAY reversal HTTP status is indeterminate");
            }
            return verifiedResult(response.body(), request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("VNPAY reversal was interrupted", exception);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("VNPAY reversal outcome is unknown", exception);
        }
    }

    Map<String, String> signedRequest(Request request) {
        requireConfiguration();
        long providerAmount = Math.multiplyExact(request.amountVnd(), 100L);
        if (request.amountVnd() <= 0 || request.originalPaidAt() == null
                || request.originalProviderTransactionNo() == null) {
            throw new IllegalArgumentException("VNPAY full reversal request is incomplete");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", request.requestReference());
        fields.put("vnp_Version", VERSION);
        fields.put("vnp_Command", COMMAND);
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_TransactionType", FULL_REVERSAL);
        fields.put("vnp_TxnRef", request.originalMerchantReference());
        fields.put("vnp_Amount", Long.toString(providerAmount));
        fields.put("vnp_TransactionNo", request.originalProviderTransactionNo());
        fields.put("vnp_TransactionDate", format(request.originalPaidAt()));
        fields.put("vnp_CreateBy", createBy);
        fields.put("vnp_CreateDate", format(request.requestedAt()));
        fields.put("vnp_IpAddr", serverIp);
        fields.put("vnp_OrderInfo", "Void " + request.originalMerchantReference());
        fields.put("vnp_SecureHash", crypto.hmac(requestChecksum(fields)));
        return fields;
    }

    Result verifiedResult(String body, Request request) throws Exception {
        JsonNode root = json.readTree(body);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : new String[] {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"}) {
            JsonNode value = root.get(key);
            fields.put(key, value == null || value.isNull() ? "" : value.asString());
        }
        String supplied = text(root, "vnp_SecureHash");
        if (!crypto.verifies(responseChecksum(fields), supplied)) {
            throw new IllegalArgumentException("Invalid VNPAY reversal signature");
        }
        if (!tmnCode.equals(fields.get("vnp_TmnCode"))
                || !COMMAND.equals(fields.get("vnp_Command"))
                || !FULL_REVERSAL.equals(fields.get("vnp_TransactionType"))
                || !request.originalMerchantReference().equals(fields.get("vnp_TxnRef"))
                || Math.multiplyExact(request.amountVnd(), 100L) != Long.parseLong(fields.get("vnp_Amount"))) {
            throw new IllegalArgumentException("VNPAY reversal response does not match the request");
        }
        String responseCode = fields.get("vnp_ResponseCode");
        String status = fields.get("vnp_TransactionStatus");
        Outcome outcome = classify(responseCode, status);
        return new Result(outcome, fields.get("vnp_ResponseId"), responseCode, status,
                fields.get("vnp_TransactionNo"), VnPayCrypto.sha256(responseChecksum(fields)));
    }

    static Outcome classify(String responseCode, String status) {
        if ("00".equals(responseCode)) {
            return switch (status) {
                case "00" -> Outcome.SUCCEEDED;
                case "01", "05", "06" -> Outcome.UNKNOWN;
                case "02", "09" -> Outcome.DEFINITIVE_FAILED;
                default -> Outcome.REVIEW_REQUIRED;
            };
        }
        if ("94".equals(responseCode) || "99".equals(responseCode)) return Outcome.UNKNOWN;
        return Outcome.DEFINITIVE_FAILED;
    }

    static String requestChecksum(Map<String, String> f) {
        return String.join("|", f.get("vnp_RequestId"), f.get("vnp_Version"), f.get("vnp_Command"),
                f.get("vnp_TmnCode"), f.get("vnp_TransactionType"), f.get("vnp_TxnRef"), f.get("vnp_Amount"),
                f.get("vnp_TransactionNo"), f.get("vnp_TransactionDate"), f.get("vnp_CreateBy"),
                f.get("vnp_CreateDate"), f.get("vnp_IpAddr"), f.get("vnp_OrderInfo"));
    }

    static String responseChecksum(Map<String, String> f) {
        return String.join("|", f.get("vnp_ResponseId"), f.get("vnp_Command"), f.get("vnp_ResponseCode"),
                f.get("vnp_Message"), f.get("vnp_TmnCode"), f.get("vnp_TxnRef"), f.get("vnp_Amount"),
                f.get("vnp_BankCode"), f.get("vnp_PayDate"), f.get("vnp_TransactionNo"),
                f.get("vnp_TransactionType"), f.get("vnp_TransactionStatus"), f.get("vnp_OrderInfo"));
    }

    private void requireConfiguration() {
        if (tmnCode.isEmpty() || apiUrl.isEmpty() || createBy.isEmpty() || serverIp.isEmpty()) {
            throw new IllegalStateException("VNPAY reversal is not configured");
        }
    }
    private static String format(Instant value) { return DATE_TIME.format(value.atZone(VnPayPaymentProvider.VNPAY_ZONE)); }
    private static String text(JsonNode root, String key) { JsonNode value = root.get(key); return value == null ? null : value.asString(); }
}
