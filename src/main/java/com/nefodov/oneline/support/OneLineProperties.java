package com.nefodov.oneline.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oneline")
public record OneLineProperties(Participant participant, Session session, Crypto crypto, RateLimit rateLimit) {

    public record Participant(Duration activityWindow) {}

    public record Session(String cookieName, boolean cookieSecure, String cookieSameSite, Duration cookieMaxAge) {}

    public record Crypto(Algorithm algorithm) {
        public enum Algorithm {
            AES_GCM,
            PLAINTEXT
        }
    }

    public record RateLimit(Bucket createChat, Bucket join) {
        public record Bucket(int capacity, Duration refillPeriod) {}
    }
}
