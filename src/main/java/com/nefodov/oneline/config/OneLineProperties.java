package com.nefodov.oneline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oneline")
public record OneLineProperties(Participant participant, RateLimit rateLimit, Retention retention, Storage storage, Attachments attachments) {

    public record Participant(Duration activityWindow) {}

    public record RateLimit(Bucket createChat, Bucket join, Bucket message, Bucket attachment, Bucket uploadBytes) {

        public record Bucket(long capacity, Duration refillPeriod) {}
    }

    public record Retention(Duration inactivityWindow, String cron, String zone) {}

    public record Storage(String endpoint, String publicEndpoint, String accessKey, String secretKey, String bucket, Duration presignTtl, long maxFileSize, Duration unconfirmedTtl) {}

    public record Attachments(boolean enabled, Duration ttl) {}
}
