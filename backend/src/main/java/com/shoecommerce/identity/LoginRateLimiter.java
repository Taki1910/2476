package com.shoecommerce.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class LoginRateLimiter {
    public record Policy(int pairLimit, Duration pairWindow, int sourceLimit, Duration sourceWindow, int maxBuckets) { }
    public record Decision(boolean allowed, long retryAfterSeconds) { }
    private record Key(String login, String source) { }
    private record Bucket(int count, Instant expiresAt) { }
    private final Clock clock;
    private final Policy policy;
    private final Map<Key,Bucket> buckets = new HashMap<>();

    public LoginRateLimiter(Clock clock, Policy policy) {
        this.clock=Objects.requireNonNull(clock);
        this.policy=Objects.requireNonNull(policy);
        if(policy.pairLimit()<1 || policy.sourceLimit()<1 || policy.maxBuckets()<2
                || policy.pairWindow()==null || policy.sourceWindow()==null
                || policy.pairWindow().isNegative() || policy.pairWindow().isZero()
                || policy.sourceWindow().isNegative() || policy.sourceWindow().isZero()) {
            throw new IllegalArgumentException("Login throttle policy must have positive limits and windows, and at least two buckets");
        }
        clock.instant().plus(policy.pairWindow());
        clock.instant().plus(policy.sourceWindow());
    }

    public synchronized Decision acquire(String login, String sourceAddress) {
        String normalized=UserAccount.normalizeLogin(login);
        // Oversized, invalid account identifiers share a bounded key; no password is retained.
        if(normalized.length()>254) normalized="\u0000";
        String source=sourceAddress==null?"":sourceAddress;
        if(source.length()>64) source=source.substring(0,64);
        Key pair=new Key(normalized,source), address=new Key(null,source);
        Instant now=clock.instant();
        Bucket pairBucket=active(pair,now), sourceBucket=active(address,now);
        long retry=0;
        if(pairBucket!=null && pairBucket.count()>=policy.pairLimit()) retry=secondsUntil(now,pairBucket.expiresAt());
        if(sourceBucket!=null && sourceBucket.count()>=policy.sourceLimit()) retry=Math.max(retry,secondsUntil(now,sourceBucket.expiresAt()));
        if(retry>0) return new Decision(false,retry);
        int newKeys=(buckets.containsKey(pair)?0:1)+(buckets.containsKey(address)?0:1);
        if(buckets.size()+newKeys>policy.maxBuckets()) {
            // ponytail: bounded O(maxBuckets) scan on capacity pressure; use an expiry queue if measured load warrants it.
            buckets.values().removeIf(bucket -> !now.isBefore(bucket.expiresAt()));
            newKeys=(buckets.containsKey(pair)?0:1)+(buckets.containsKey(address)?0:1);
            if(buckets.size()+newKeys>policy.maxBuckets()) {
                Instant earliest=buckets.values().stream().map(Bucket::expiresAt).min(Instant::compareTo).orElseThrow();
                return new Decision(false,secondsUntil(now,earliest));
            }
        }
        buckets.put(pair,increment(pairBucket,now,policy.pairWindow()));
        buckets.put(address,increment(sourceBucket,now,policy.sourceWindow()));
        return new Decision(true,0);
    }
    private Bucket active(Key key,Instant now) {
        Bucket bucket=buckets.get(key);
        return bucket!=null && now.isBefore(bucket.expiresAt())?bucket:null;
    }
    private static Bucket increment(Bucket bucket,Instant now,Duration window) {
        return bucket==null?new Bucket(1,now.plus(window)):new Bucket(bucket.count()+1,bucket.expiresAt());
    }
    private static long secondsUntil(Instant now,Instant expiry) {
        Duration remaining=Duration.between(now,expiry);
        return Math.max(1,remaining.getSeconds()+(remaining.getNano()>0?1:0));
    }
}
