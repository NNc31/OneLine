package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatSession;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class MessageService {

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 200;
    private static final int MAX_CONTENT_LENGTH = 8192;

    private final MessageRepository messageRepository;

    @Transactional
    public Message send(ChatSession session, UUID clientMessageId, byte[] content) {
        validateContent(content);
        Chat chat = session.chat();
        return messageRepository.findByChatAndClientMessageId(chat, clientMessageId).orElseGet(() -> persist(session, clientMessageId, content));
    }

    @Transactional
    public int deleteExpired() {
        return messageRepository.deleteExpiredByChatTtl();
    }

    @Transactional(readOnly = true)
    public List<Message> history(ChatSession session, Long beforeId, Integer limit) {
        Chat chat = session.chat();
        int effectiveLimit = resolveHistoryLimit(limit);
        return beforeId == null
                ? messageRepository.findByChatOrderByIdDesc(chat, Limit.of(effectiveLimit))
                : messageRepository.findByChatAndIdLessThanOrderByIdDesc(chat, beforeId, Limit.of(effectiveLimit));
    }

    private Message persist(ChatSession session, UUID clientMessageId, byte[] content) {
        Message message = new Message();
        message.setChat(session.chat());
        message.setParticipant(session.participant());
        message.setClientMessageId(clientMessageId);
        message.setContent(content);
        return messageRepository.save(message);
    }

    private void validateContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (content.length > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Message content is too long");
        }
    }

    private int resolveHistoryLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.clamp(requested, 1, MAX_HISTORY_LIMIT);
    }
}
