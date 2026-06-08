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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class AttachmentCleanupServiceTest {

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");

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
    @DisplayName("Removes the legacy v1 row and its MinIO object")
    void sweepExpiredRemovesLegacyObjectAndRow() throws Exception {
        Chat chat = persistChat(60L);
        ChatParticipant member = persistParticipant(chat);
        String objectKey = putObject();
        Attachment attachment = persistLegacyAttachment(chat, member, objectKey, true, FAR_PAST);
        int swept = cleanupService.sweepExpiredByChatTtl();
        assertTrue(swept >= 1);
        em.clear();
        assertTrue(attachmentRepository.findById(attachment.getId()).isEmpty());
        assertTrue(storage.objectSize(objectKey).isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("Removes every chunk object and the parent row")
    void sweepExpiredRemovesAllChunksAndRow() throws Exception {
        Chat chat = persistChat(60L);
        ChatParticipant member = persistParticipant(chat);
        List<String> objectKeys = List.of(putObject(), putObject(), putObject());
        Attachment attachment = persistChunkedAttachment(chat, member, objectKeys, true, FAR_PAST);
        int swept = cleanupService.sweepExpiredByChatTtl();
        assertTrue(swept >= 1);
        em.clear();
        assertTrue(attachmentRepository.findById(attachment.getId()).isEmpty());
        for (String key : objectKeys) {
            assertTrue(storage.objectSize(key).isEmpty(), () -> "chunk " + key + " should be deleted");
        }
    }

    @Test
    @Transactional
    @DisplayName("Removes abandoned uploads and their chunk objects")
    void sweepUnconfirmedRemovesAbandonedChunks() throws Exception {
        Chat chat = persistChat(null);
        ChatParticipant member = persistParticipant(chat);
        List<String> objectKeys = List.of(putObject(), putObject());
        Attachment attachment = persistChunkedAttachment(chat, member, objectKeys, false, FAR_PAST);
        int swept = cleanupService.sweepUnconfirmed();
        assertTrue(swept >= 1);
        em.clear();
        assertTrue(attachmentRepository.findById(attachment.getId()).isEmpty());
        for (String key : objectKeys) {
            assertTrue(storage.objectSize(key).isEmpty());
        }
    }

    @Test
    @Transactional
    @DisplayName("Removes attachments older than the configured TTL")
    void sweepExpiredByAttachmentTtlRemovesOldRow() throws Exception {
        Chat chat = persistChat(null);
        ChatParticipant member = persistParticipant(chat);
        String objectKey = putObject();
        Attachment attachment = persistLegacyAttachment(chat, member, objectKey, true, FAR_PAST);
        int swept = cleanupService.sweepExpiredByAttachmentTtl();
        assertTrue(swept >= 1);
        em.clear();
        assertTrue(attachmentRepository.findById(attachment.getId()).isEmpty());
        assertTrue(storage.objectSize(objectKey).isEmpty());
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

    private Attachment persistLegacyAttachment(Chat chat, ChatParticipant participant, String objectKey, boolean confirmed, Instant createdAt) {
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

    private Attachment persistChunkedAttachment(Chat chat, ChatParticipant participant, List<String> objectKeys, boolean confirmed, Instant createdAt) {
        Attachment attachment = new Attachment();
        attachment.setChat(chat);
        attachment.setParticipant(participant);
        attachment.setObjectKey(null);
        attachment.setCiphertextSize(4L * objectKeys.size());
        attachment.setConfirmed(confirmed);
        attachment.setCreatedAt(createdAt);
        for (int i = 0; i < objectKeys.size(); i++) {
            AttachmentChunk chunk = new AttachmentChunk();
            chunk.setChunkIndex(i);
            chunk.setObjectKey(objectKeys.get(i));
            chunk.setCiphertextSize(4);
            attachment.addChunk(chunk);
        }
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
