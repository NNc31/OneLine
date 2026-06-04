package com.nefodov.oneline.chat;

import com.nefodov.oneline.chat.dto.ParticipantView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PresenceServiceTest {

    private static final long T0 = 1_000_000_000L;
    private static final long STALE_MS = 60_000L;
    private static final AtomicLong NOW = new AtomicLong();

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        public Clock clock() {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public long millis() { return NOW.get(); }
                @Override public Instant instant() { return Instant.ofEpochMilli(NOW.get()); }
            };
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    PresenceService presence;

    @BeforeEach
    void resetClock() {
        NOW.set(T0);
    }

    @Test
    @DisplayName("Lists a participant that was just marked online")
    void onlineListsMarkedParticipant() {
        long chatId = 1001L;
        presence.markOnline(chatId, 7L, "Alice");
        assertThat(presence.online(chatId)).extracting(ParticipantView::id, ParticipantView::displayName).containsExactly(Tuple.tuple(7L, "Alice"));
    }

    @Test
    @DisplayName("Removes a participant with last heartbeat past the stale window")
    void evictStaleRemovesLapsed() {
        long chatId = 1002L;
        presence.markOnline(chatId, 7L, "Alice");
        NOW.set(T0 + STALE_MS + 1_000L);
        int evicted = presence.evictStale(chatId);
        assertThat(evicted).isEqualTo(1);
        assertThat(presence.online(chatId)).isEmpty();
    }

    @Test
    @DisplayName("Fresh heartbeat survives eviction")
    void freshHeartbeatSurvivesEviction() {
        long chatId = 1003L;
        presence.markOnline(chatId, 1L, "Stale");
        NOW.set(T0 + 40_000L);
        presence.markOnline(chatId, 2L, "Fresh");
        NOW.set(T0 + STALE_MS + 1_000L);
        int evicted = presence.evictStale(chatId);
        assertThat(evicted).isEqualTo(1);
        assertThat(presence.online(chatId)).extracting(ParticipantView::displayName).containsExactly("Fresh");
    }

    @Test
    @DisplayName("Removes a participant immediately")
    void markOfflineRemovesImmediately() {
        long chatId = 1004L;
        presence.markOnline(chatId, 7L, "Alice");
        presence.markOnline(chatId, 8L, "Bob");
        presence.markOffline(chatId, 7L);
        assertThat(presence.online(chatId)).extracting(ParticipantView::displayName).containsExactly("Bob");
    }
}
