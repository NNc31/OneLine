package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class MessageRepositoryTest {

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @PersistenceContext
    EntityManager em;

    @Autowired
    MessageRepository messageRepository;

    @Test
    @DisplayName("Removes only messages older than their chat TTL")
    void deletesOnlyExpiredForTtlChats() {
        Chat chat = persistChat(60L);
        ChatParticipant member = persistParticipant(chat);
        Message stale = persistMessage(chat, member, FAR_PAST);
        Message fresh = persistMessage(chat, member, FAR_FUTURE);
        int deleted = messageRepository.deleteExpiredByChatTtl();
        assertThat(deleted).isEqualTo(1);
        em.clear();
        assertThat(messageRepository.findById(stale.getId())).isEmpty();
        assertThat(messageRepository.findById(fresh.getId())).isPresent();
    }

    @Test
    @DisplayName("Never touches chats with TTL disabled")
    void keepsMessagesWhenTtlDisabled() {
        Chat chat = persistChat(null);
        ChatParticipant member = persistParticipant(chat);
        Message ancient = persistMessage(chat, member, FAR_PAST);
        int deleted = messageRepository.deleteExpiredByChatTtl();
        assertThat(deleted).isZero();
        em.clear();
        assertThat(messageRepository.findById(ancient.getId())).isPresent();
    }

    private Chat persistChat(Long ttlSeconds) {
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setChatTokenHash(randomBytes());
        chat.setMessageTtlSeconds(ttlSeconds);
        em.persist(chat);
        em.flush();
        return chat;
    }

    private ChatParticipant persistParticipant(Chat chat) {
        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        participant.setSessionTokenHash(randomBytes());
        participant.setDisplayName("Tester");
        em.persist(participant);
        em.flush();
        return participant;
    }

    private Message persistMessage(Chat chat, ChatParticipant participant, Instant createdAt) {
        Message message = new Message();
        message.setChat(chat);
        message.setParticipant(participant);
        message.setClientMessageId(UUID.randomUUID());
        message.setContent(new byte[]{1, 2, 3});
        message.setCreatedAt(createdAt);
        em.persist(message);
        em.flush();
        return message;
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
