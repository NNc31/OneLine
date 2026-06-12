package com.nefodov.oneline.chat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ChatLifecycleSchedulerTest {

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        public Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("oneline.storage.endpoint", MINIO::getS3URL);
        registry.add("oneline.storage.public-endpoint", MINIO::getS3URL);
        registry.add("oneline.storage.access-key", MINIO::getUserName);
        registry.add("oneline.storage.secret-key", MINIO::getPassword);
    }

    @PersistenceContext
    EntityManager em;

    @Autowired
    ChatLifecycleScheduler scheduler;

    @Autowired
    ChatRepository chatRepository;

    @Test
    @Transactional
    @DisplayName("Removes a chat with creation date older than inactivity window")
    void removesAncientChat() {
        Chat ancient = persistChatAt(FAR_PAST);
        Long ancientId = ancient.getId();
        scheduler.deleteInactiveChats();
        em.flush();
        em.clear();
        assertTrue(chatRepository.findById(ancientId).isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("No action on a fresh chat")
    void keepsFreshChat() {
        Chat fresh = persistChatAt(NOW);
        Long freshId = fresh.getId();
        scheduler.deleteInactiveChats();
        em.flush();
        em.clear();
        assertFalse(chatRepository.findById(freshId).isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("No error when there are no chats to sweep")
    void runsCleanWithNothingToDo() {
        assertDoesNotThrow(() -> scheduler.deleteInactiveChats());
    }

    private Chat persistChatAt(Instant createdAt) {
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setChatTokenHash(randomBytes());
        chat.setCreatedAt(createdAt);
        em.persist(chat);
        em.flush();
        return chat;
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
