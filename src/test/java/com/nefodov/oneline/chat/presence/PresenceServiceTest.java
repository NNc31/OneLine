package com.nefodov.oneline.chat.presence;

import com.nefodov.oneline.chat.PresenceService;
import com.nefodov.oneline.chat.dto.ParticipantView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class PresenceServiceTest {

    private static final long T0 = 1_000_000_000L;
    private static final long STALE_MS = 60_000L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @MockitoBean
    Clock clock;

    @Autowired
    PresenceService presence;

    private final AtomicLong now = new AtomicLong();

    @BeforeEach
    void fixClock() {
        when(clock.millis()).thenAnswer(invocation -> now.get());
        now.set(T0);
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
        now.set(T0 + STALE_MS + 1_000L);
        int evicted = presence.evictStale(chatId);
        assertThat(evicted).isEqualTo(1);
        assertThat(presence.online(chatId)).isEmpty();
    }

    @Test
    @DisplayName("Fresh heartbeat survives eviction")
    void freshHeartbeatSurvivesEviction() {
        long chatId = 1003L;
        presence.markOnline(chatId, 1L, "Stale");
        now.set(T0 + 40_000L);
        presence.markOnline(chatId, 2L, "Fresh");
        now.set(T0 + STALE_MS + 1_000L);
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
