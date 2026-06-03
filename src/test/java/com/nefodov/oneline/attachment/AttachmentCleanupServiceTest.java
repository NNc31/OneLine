package com.nefodov.oneline.attachment;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.config.OneLineProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AttachmentCleanupServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static MinIOContainer MINIO = new MinIOContainer("minio/minio");

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
    AttachmentCleanupService cleanupService;

    @Autowired
    AttachmentRepository attachmentRepository;

    @Autowired
    AttachmentStorage storage;

    @Autowired
    MinioClient minioClient;

    @Autowired
    OneLineProperties properties;

    @Test
    @Transactional
    @DisplayName("Removes both the row and the MinIO object")
    void sweepExpiredRemovesObjectAndRow() throws Exception {
        Chat chat = persistChat(60L);
        ChatParticipant member = persistParticipant(chat);
        String objectKey = putObject();
        Attachment attachment = persistAttachment(chat, member, objectKey, true, Instant.now().minus(5, ChronoUnit.MINUTES));
        int swept = cleanupService.sweepExpiredByChatTtl();
        assertThat(swept).isGreaterThanOrEqualTo(1);
        em.clear();
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
        assertThat(storage.objectSize(objectKey)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Removes abandoned uploads and their objects")
    void sweepUnconfirmedRemovesAbandoned() throws Exception {
        Chat chat = persistChat(null);
        ChatParticipant member = persistParticipant(chat);
        String objectKey = putObject();
        Attachment attachment = persistAttachment(chat, member, objectKey, false, Instant.now().minus(1, ChronoUnit.HOURS));
        int swept = cleanupService.sweepUnconfirmed();
        assertThat(swept).isGreaterThanOrEqualTo(1);
        em.clear();
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
        assertThat(storage.objectSize(objectKey)).isEmpty();
    }

    private String putObject() throws Exception {
        String objectKey = UUID.randomUUID().toString();
        byte[] data = {1, 2, 3, 4};
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.storage().bucket())
                .object(objectKey)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .build());
        return objectKey;
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

    private Attachment persistAttachment(Chat chat, ChatParticipant participant, String objectKey, boolean confirmed, Instant createdAt) {
        Attachment attachment = new Attachment();
        attachment.setChat(chat);
        attachment.setParticipant(participant);
        attachment.setObjectKey(objectKey);
        attachment.setCiphertextSize(4);
        attachment.setConfirmed(confirmed);
        attachment.setCreatedAt(createdAt);
        em.persist(attachment);
        em.flush();
        return attachment;
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
