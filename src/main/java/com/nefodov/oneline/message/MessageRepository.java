package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChatOrderByIdDesc(Chat chat, Limit limit);

    List<Message> findByChatAndIdLessThanOrderByIdDesc(Chat chat, Long beforeId, Limit limit);

    Optional<Message> findByChatAndClientMessageId(Chat chat, UUID clientMessageId);
}
