package com.shoecommerce.payment;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public final class TestVoidProvider implements VoidProvider {
    private final AtomicReference<Outcome> next = new AtomicReference<>(Outcome.SUCCEEDED);
    private static final AtomicInteger calls = new AtomicInteger();

    @Override public Result reverse(Request request) {
        int call = calls.incrementAndGet();
        Outcome outcome = next.getAndSet(Outcome.SUCCEEDED);
        return new Result(outcome, request.requestReference(), "00",
                switch (outcome) {
                    case SUCCEEDED -> "00";
                    case DEFINITIVE_FAILED -> "02";
                    case UNKNOWN -> "05";
                    case REVIEW_REQUIRED -> "07";
                }, "900000" + call, "0".repeat(64));
    }

    public void next(Outcome outcome) { next.set(outcome); }
    public int calls() { return calls.get(); }
}
