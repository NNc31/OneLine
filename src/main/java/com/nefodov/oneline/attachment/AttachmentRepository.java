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
            SELECT a.id FROM attachments a JOIN chats c ON a.chat_id = c.id
            WHERE c.message_ttl_seconds IS NOT NULL AND a.created_at < now() - make_interval(secs => c.message_ttl_seconds)
            """, nativeQuery = true)
    List<Long> findExpiredAttachmentIdsByChatTtl();

    @Query("SELECT a.id FROM Attachment a WHERE a.confirmed = false AND a.createdAt < :cutoff")
    List<Long> findUnconfirmedAttachmentIdsOlderThan(@Param("cutoff") Instant cutoff);

    @Query("SELECT a.id FROM Attachment a WHERE a.createdAt < :cutoff")
    List<Long> findAttachmentIdsOlderThan(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT a.id FROM Attachment a
            WHERE a.chat.createdAt < :cutoff AND NOT EXISTS (SELECT 1 FROM Message m WHERE m.chat = a.chat AND m.createdAt > :cutoff)
            """)
    List<Long> findAttachmentIdsForInactiveChatsBefore(@Param("cutoff") Instant cutoff);

    @Query(value = """
            SELECT object_key FROM attachments WHERE id IN (:ids) AND object_key IS NOT NULL
            UNION ALL
            SELECT object_key FROM attachment_chunks WHERE attachment_id IN (:ids)
            """, nativeQuery = true)
    List<String> findAllObjectKeysByAttachmentIds(@Param("ids") Collection<Long> ids);

    @Transactional
    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.id IN :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);
}
