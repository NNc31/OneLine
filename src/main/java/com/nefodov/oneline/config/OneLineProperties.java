package com.nefodov.oneline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oneline")
public record OneLineProperties(Participant participant, Session session, RateLimit rateLimit, Retention retention, Storage storage) {

    public record Participant(Duration activityWindow) {}

    public record Session(String cookieName, boolean cookieSecure, String cookieSameSite, Duration cookieMaxAge) {}

    public record RateLimit(Bucket createChat, Bucket join, Bucket message, Bucket attachment) {

        public record Bucket(int capacity, Duration refillPeriod) {}
    }

    public record Retention(Duration inactivityWindow, String cron, String zone) {}

    public record Storage(String endpoint, String publicEndpoint, String accessKey, String secretKey, String bucket, Duration presignTtl, long maxFileSize, Duration unconfirmedTtl) {}
}
