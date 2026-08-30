package com.shoecommerce.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
final class DemoPaymentProvider implements PaymentProvider {
    @Override public String paymentUrl(Request request) {
        return "/api/v1/payments/demo/complete?reference=" + URLEncoder.encode(request.merchantReference(), StandardCharsets.UTF_8);
    }
    @Override public VerifiedResult verify(Map<String, String> ignored) { throw new UnsupportedOperationException("Demo payment uses its isolated completion route"); }
}
