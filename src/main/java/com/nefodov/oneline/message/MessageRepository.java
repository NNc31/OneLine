package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChatOrderByIdDesc(Chat chat, Limit limit);

    List<Message> findByChatAndIdLessThanOrderByIdDesc(Chat chat, Long beforeId, Limit limit);

    Optional<Message> findByChatAndClientMessageId(Chat chat, UUID clientMessageId);

    @Modifying
    @Query(value = """
            DELETE FROM messages m USING chats c WHERE m.chat_id = c.id
              AND c.message_ttl_seconds IS NOT NULL
              AND m.created_at < now() - make_interval(secs => c.message_ttl_seconds)
            """, nativeQuery = true)
    int deleteExpiredByChatTtl();
}
