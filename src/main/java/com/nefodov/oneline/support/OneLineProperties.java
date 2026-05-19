package com.nefodov.oneline.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oneline")
public record OneLineProperties(Participant participant, Session session, RateLimit rateLimit, Retention retention) {

    public record Participant(Duration activityWindow) {}

    public record Session(String cookieName, boolean cookieSecure, String cookieSameSite, Duration cookieMaxAge) {}

    public record RateLimit(Bucket createChat, Bucket join, Bucket message) {

        public record Bucket(int capacity, Duration refillPeriod) {}
    }

    public record Retention(Duration inactivityWindow, String cron, String zone) {}
}
