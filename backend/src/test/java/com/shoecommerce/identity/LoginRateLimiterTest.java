package com.shoecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    private static LoginRateLimiter.Policy policy(int pair,int source,int buckets) {
        return new LoginRateLimiter.Policy(pair,Duration.ofMinutes(15),source,Duration.ofMinutes(1),buckets);
    }
    @Test void normalizedPairLimitExpiresExactlyAndDenialDoesNotExtendIt() {
        var clock=new MutableClock();var limiter=new LoginRateLimiter(clock,policy(5,30,10000));
        for(int i=0;i<5;i++) assertThat(limiter.acquire(" User ","source").allowed()).isTrue();
        clock.at.set(Instant.EPOCH.plusMillis(500));
        var denied=limiter.acquire("user","source");
        assertThat(denied.allowed()).isFalse();assertThat(denied.retryAfterSeconds()).isEqualTo(900);
        clock.at.set(Instant.EPOCH.plusSeconds(899));
        assertThat(limiter.acquire("USER","source").retryAfterSeconds()).isEqualTo(1);
        clock.at.set(Instant.EPOCH.plusSeconds(900));
        assertThat(limiter.acquire("user","source").allowed()).isTrue();
    }
    @Test void deniedPairDoesNotConsumeSourceAndAnotherSourceIsIndependent() {
        var limiter=new LoginRateLimiter(new MutableClock(),policy(1,3,100));
        assertThat(limiter.acquire("a","one").allowed()).isTrue();
        for(int i=0;i<10;i++) assertThat(limiter.acquire("a","one").allowed()).isFalse();
        assertThat(limiter.acquire("b","one").allowed()).isTrue();
        assertThat(limiter.acquire("c","one").allowed()).isTrue();
        assertThat(limiter.acquire("d","one").allowed()).isFalse();
        assertThat(limiter.acquire("a","two").allowed()).isTrue();
    }
    @Test void concurrentRequestsCannotOvershootTheLastSlot() throws Exception {
        var limiter=new LoginRateLimiter(new MutableClock(),policy(5,30,100));
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var calls=IntStream.range(0,50).mapToObj(i->(Callable<Boolean>)()->limiter.acquire("user","source").allowed()).toList();
            int admitted=0;for(var result:executor.invokeAll(calls)) if(result.get())admitted++;
            assertThat(admitted).isEqualTo(5);
        }
    }
    @Test void fullCapacityRejectsNewKeysWithoutEvictingLiveLimitsAndRecoversAfterExpiry() {
        var clock=new MutableClock();var limiter=new LoginRateLimiter(clock,policy(1,30,4));
        assertThat(limiter.acquire("a","one").allowed()).isTrue();
        assertThat(limiter.acquire("b","two").allowed()).isTrue();
        assertThat(limiter.acquire("c","three").allowed()).isFalse();
        assertThat(limiter.acquire("c","three").retryAfterSeconds()).isEqualTo(60);
        clock.at.set(Instant.EPOCH.plusSeconds(60));
        assertThat(limiter.acquire("c","three").allowed()).isTrue();
        assertThat(limiter.acquire("a","one").allowed()).isFalse();
    }
    @Test void invalidPolicyFailsAtConstruction() {
        assertThatThrownBy(()->new LoginRateLimiter(new MutableClock(),policy(0,30,10))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new LoginRateLimiter(new MutableClock(),new LoginRateLimiter.Policy(5,Duration.ZERO,30,Duration.ofMinutes(1),100))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new LoginRateLimiter(new MutableClock(),policy(5,30,1))).isInstanceOf(IllegalArgumentException.class);
    }
    static final class MutableClock extends Clock {
        final AtomicReference<Instant> at=new AtomicReference<>(Instant.EPOCH);
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(at.get(),zone);}
        @Override public Instant instant(){return at.get();}
    }
}
