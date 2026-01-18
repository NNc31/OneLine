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
    private static final int MAX_CONTENT_LENGTH = 4000;

    private final MessageRepository messageRepository;
    private final MessageContentCodec contentCodec;

    @Transactional
    public StoredMessage send(ChatSession session, UUID clientMessageId, String content) {
        validateContent(content);
        Chat chat = session.chat();
        byte[] key = session.messageKey();
        return messageRepository.findByChatAndClientMessageId(chat, clientMessageId)
                .map(existing -> new StoredMessage(existing, contentCodec.decode(key, existing.getContent())))
                .orElseGet(() -> persist(session, clientMessageId, content));
    }

    @Transactional(readOnly = true)
    public List<StoredMessage> history(ChatSession session, Long beforeId, Integer limit) {
        Chat chat = session.chat();
        byte[] key = session.messageKey();
        int effectiveLimit = resolveHistoryLimit(limit);
        List<Message> rows = beforeId == null
                ? messageRepository.findByChatOrderByIdDesc(chat, Limit.of(effectiveLimit))
                : messageRepository.findByChatAndIdLessThanOrderByIdDesc(chat, beforeId, Limit.of(effectiveLimit));
        return rows.stream().map(m -> new StoredMessage(m, contentCodec.decode(key, m.getContent()))).toList();
    }

    private StoredMessage persist(ChatSession session, UUID clientMessageId, String content) {
        Message message = new Message();
        message.setChat(session.chat());
        message.setParticipant(session.participant());
        message.setClientMessageId(clientMessageId);
        message.setContent(contentCodec.encode(session.messageKey(), content));
        Message saved = messageRepository.save(message);
        return new StoredMessage(saved, content);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Message content is too long");
        }
    }

    private int resolveHistoryLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.max(1, Math.min(MAX_HISTORY_LIMIT, requested));
    }

    public record StoredMessage(Message message, String plaintext) {
    }
}
