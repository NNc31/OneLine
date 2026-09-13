package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.chat.ChatParticipant;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.exception.NotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class MessageService {

    static final String TYPE_CHAT = "chat";
    static final String TYPE_JOINED = "joined";
    static final String TYPE_DELETED = "deleted";

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
    public Message createJoinNotice(Chat chat, ChatParticipant participant) {
        Message message = new Message();
        message.setChat(chat);
        message.setParticipant(participant);
        message.setClientMessageId(UUID.randomUUID());
        message.setContent(new byte[0]);
        message.setType(TYPE_JOINED);
        return messageRepository.save(message);
    }

    @Transactional
    public Message delete(ChatSession session, Long messageId) {
        Message message = messageRepository.findByIdAndChat(messageId, session.chat()).orElseThrow(() -> new NotFoundException("Message not found"));
        if (!message.getParticipant().getId().equals(session.participant().getId())) {
            throw new NotFoundException("Message not found");
        }
        if (TYPE_DELETED.equals(message.getType())) {
            return message;
        }
        if (!TYPE_CHAT.equals(message.getType())) {
            throw new NotFoundException("Message not found");
        }
        message.setContent(new byte[0]);
        message.setType(TYPE_DELETED);
        return message;
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
