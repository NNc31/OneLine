package com.nefodov.oneline.chat;

import com.nefodov.oneline.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class ChatParticipantJoinConcurrencyTest {

    private static final int CONTENDERS = 4;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    ChatParticipantService participantService;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatParticipantRepository participantRepository;

    @Test
    @DisplayName("Simultaneous joins under one name produce exactly one participant")
    void concurrentJoinsUnderSameNameCreateOneParticipant() throws Exception {
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setChatTokenHash(randomBytes());
        Chat saved = chatRepository.save(chat);

        CyclicBarrier startTogether = new CyclicBarrier(CONTENDERS);
        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        List<Future<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            attempts.add(pool.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                try {
                    participantService.join(saved, "Alex");
                    return true;
                } catch (ConflictException _) {
                    return false;
                }
            }));
        }
        pool.shutdown();

        int joined = 0;
        for (Future<Boolean> attempt : attempts) {
            if (Boolean.TRUE.equals(attempt.get(30, TimeUnit.SECONDS))) {
                joined++;
            }
        }

        assertEquals(1, joined);
        assertEquals(1L, participantRepository.countByChatId(saved.getId()));
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
