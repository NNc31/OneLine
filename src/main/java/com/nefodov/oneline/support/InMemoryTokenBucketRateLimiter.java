package com.nefodov.oneline.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private final OneLineProperties properties;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryTokenBucketRateLimiter(OneLineProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String bucketName, String key) {
        OneLineProperties.RateLimit.Bucket config = configFor(bucketName);
        Bucket bucket = buckets.computeIfAbsent(bucketName + "|" + key, k -> new Bucket(config.capacity(), clock.millis()));
        return bucket.tryConsume(config, clock.millis());
    }

    private OneLineProperties.RateLimit.Bucket configFor(String bucketName) {
        OneLineProperties.RateLimit limits = properties.rateLimit();
        return switch (bucketName) {
            case "create-chat" -> limits.createChat();
            case "join" -> limits.join();
            default -> throw new IllegalArgumentException("Unknown rate-limit bucket: " + bucketName);
        };
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefillMillis;

        Bucket(int initialTokens, long nowMillis) {
            this.tokens = initialTokens;
            this.lastRefillMillis = nowMillis;
        }

        synchronized boolean tryConsume(OneLineProperties.RateLimit.Bucket config, long nowMillis) {
            refill(config, nowMillis);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill(OneLineProperties.RateLimit.Bucket config, long nowMillis) {
            long elapsed = nowMillis - lastRefillMillis;
            if (elapsed <= 0) {
                return;
            }
            double refillRatePerMillis = (double) config.capacity() / refillMillis(config.refillPeriod());
            tokens = Math.min(config.capacity(), tokens + elapsed * refillRatePerMillis);
            lastRefillMillis = nowMillis;
        }

        private static long refillMillis(Duration refillPeriod) {
            long millis = refillPeriod.toMillis();
            return millis <= 0 ? 1 : millis;
        }
    }
}
