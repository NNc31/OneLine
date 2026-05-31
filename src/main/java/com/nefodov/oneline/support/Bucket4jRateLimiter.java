package com.nefodov.oneline.support;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class Bucket4jRateLimiter implements RateLimiter {

    private final ProxyManager<byte[]> proxyManager;
    private final OneLineProperties properties;

    public Bucket4jRateLimiter(ProxyManager<byte[]> proxyManager, OneLineProperties properties) {
        this.proxyManager = proxyManager;
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(String bucketName, String key) {
        OneLineProperties.RateLimit.Bucket config = configFor(bucketName);
        byte[] redisKey = ("oneline:rl:" + bucketName + ":" + key).getBytes(StandardCharsets.UTF_8);
        try {
            BucketProxy bucket = proxyManager.builder().build(redisKey, configurationSupplier(config));
            return bucket.tryConsume(1);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable for bucket '{}', allowing request: {}", bucketName, e.getMessage());
            return true;
        }
    }

    private Supplier<BucketConfiguration> configurationSupplier(OneLineProperties.RateLimit.Bucket config) {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(config.capacity())
                        .refillGreedy(config.capacity(), refillPeriod(config.refillPeriod()))
                        .build()).build();
    }

    private static Duration refillPeriod(Duration period) {
        return period == null || period.isZero() || period.isNegative() ? Duration.ofSeconds(1) : period;
    }

    private OneLineProperties.RateLimit.Bucket configFor(String bucketName) {
        OneLineProperties.RateLimit limits = properties.rateLimit();
        return switch (bucketName) {
            case "create-chat" -> limits.createChat();
            case "join" -> limits.join();
            case "message" -> limits.message();
            case "attachment" -> limits.attachment();
            default -> throw new IllegalArgumentException("Unknown rate-limit bucket: " + bucketName);
        };
    }
}
