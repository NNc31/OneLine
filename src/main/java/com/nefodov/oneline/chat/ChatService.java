package com.nefodov.oneline.chat;

import com.nefodov.oneline.support.NotFoundException;
import com.nefodov.oneline.support.TokenHasher;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final TokenHasher tokenHasher;

    @Transactional
    public Chat create(String authToken) {
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("Auth token is required");
        }
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setChatTokenHash(tokenHasher.hash(authToken));
        return chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    public Chat findActive(UUID publicId, String chatToken) {
        Chat chat = chatRepository.findByPublicId(publicId).orElseThrow(() -> new NotFoundException("Chat not found"));
        if (chat.isClosed()) {
            throw new NotFoundException("Chat not found");
        }
        if (chatToken == null || !MessageDigest.isEqual(chat.getChatTokenHash(), tokenHasher.hash(chatToken))) {
            throw new NotFoundException("Chat not found");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public Chat findByPublicId(UUID publicId) {
        Chat chat = chatRepository.findByPublicId(publicId).orElseThrow(() -> new NotFoundException("Chat not found"));
        if (chat.isClosed()) {
            throw new NotFoundException("Chat not found");
        }
        return chat;
    }

    @Transactional
    public int deleteInactiveBefore(Instant cutoff) {
        return chatRepository.deleteInactiveBefore(cutoff);
    }
}
