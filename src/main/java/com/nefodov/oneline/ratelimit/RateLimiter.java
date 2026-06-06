package com.nefodov.oneline.ratelimit;

public interface RateLimiter {

    default boolean tryAcquire(String bucketName, String key) {
        return tryAcquire(bucketName, key, 1L);
    }

    boolean tryAcquire(String bucketName, String key, long tokens);
}
