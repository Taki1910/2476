package com.shoecommerce.payment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "demo"})
public final class TestPaymentQueryProvider implements PaymentQueryProvider {
    private final AtomicReference<Outcome> next = new AtomicReference<>(Outcome.UNRESOLVED);
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    private final AtomicInteger remaining = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Gate> gate = new AtomicReference<>();
    private final String providerPrefix = "%08d".formatted(
            Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));

    @Override public Result query(Request request) {
        int call = calls.incrementAndGet();
        RuntimeException unavailable = failure.getAndSet(null);
        if (unavailable != null) throw unavailable;
        int before = remaining.getAndUpdate(value -> value > 0 ? value - 1 : 0);
        Outcome outcome = before > 0 ? next.get() : Outcome.UNRESOLVED;
        if (before <= 1) next.set(Outcome.UNRESOLVED);
        Gate blocked = gate.getAndSet(null);
        if (blocked != null) blocked.await();
        return new Result(outcome, request.requestReference(), "00", switch (outcome) {
            case CONFIRMED_PAID -> "00";
            case CONFIRMED_FAILED -> "02";
            case UNRESOLVED -> "01";
            case REVIEW_REQUIRED -> "04";
        }, providerPrefix + "%08d".formatted(call), outcome == Outcome.CONFIRMED_PAID ? request.requestedAt() : null,
                Integer.toHexString(call).repeat(64).substring(0, 64));
    }

    public void next(Outcome outcome) { repeat(outcome, 1); }
    public void repeat(Outcome outcome, int count) {
        if (count < 1) throw new IllegalArgumentException("Query result count must be positive");
        next.set(outcome); remaining.set(count); failure.set(null);
    }
    public void failNext(RuntimeException exception) { failure.set(exception); remaining.set(0); }
    public void pauseNext(CountDownLatch queried, CountDownLatch resume) { gate.set(new Gate(queried, resume)); }
    public int calls() { return calls.get(); }

    private record Gate(CountDownLatch queried, CountDownLatch resume) {
        void await() {
            queried.countDown();
            try {
                if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for QueryDr test gate");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }
}
