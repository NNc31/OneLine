package com.nefodov.oneline.support;

public interface RateLimiter {

    boolean tryAcquire(String bucketName, String key);
}
