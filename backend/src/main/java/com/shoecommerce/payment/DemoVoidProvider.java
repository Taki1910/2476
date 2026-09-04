package com.shoecommerce.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
final class DemoVoidProvider implements VoidProvider {
    @Override public Result reverse(Request request) {
        return new Result(Outcome.SUCCEEDED, request.requestReference(), "00", "00",
                "D" + request.requestReference().substring(Math.max(0, request.requestReference().length() - 31)), "demo");
    }
}
