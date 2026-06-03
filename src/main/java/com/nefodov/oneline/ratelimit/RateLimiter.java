package com.nefodov.oneline.ratelimit;

public interface RateLimiter {

    boolean tryAcquire(String bucketName, String key);
}
