package com.shoecommerce.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
final class DemoVoidProvider implements VoidProvider {
    @Override public Result reverse(Request request) {
        return new Result(Outcome.SUCCEEDED, request.requestReference(), "00", "00", "DEMO-" + request.requestReference(), "demo");
    }
}
