package com.nefodov.oneline.attachment;

import com.nefodov.oneline.chat.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByIdAndChat(Long id, Chat chat);

    @Query(value = """
            SELECT a.object_key FROM attachments a JOIN chats c ON a.chat_id = c.id
            WHERE c.message_ttl_seconds IS NOT NULL AND a.created_at < now() - make_interval(secs => c.message_ttl_seconds)
            """, nativeQuery = true)
    List<String> findExpiredObjectKeysByChatTtl();

    @Query("SELECT a.objectKey FROM Attachment a WHERE a.confirmed = false AND a.createdAt < :cutoff")
    List<String> findUnconfirmedObjectKeysOlderThan(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT a.objectKey FROM Attachment a
            WHERE a.chat.createdAt < :cutoff
            AND NOT EXISTS (SELECT 1 FROM Message m WHERE m.chat = a.chat AND m.createdAt > :cutoff)
            """)
    List<String> findObjectKeysForInactiveChatsBefore(@Param("cutoff") Instant cutoff);

    @Transactional
    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.objectKey IN :keys")
    int deleteByObjectKeys(@Param("keys") Collection<String> keys);
}
