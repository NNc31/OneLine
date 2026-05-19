package com.nefodov.oneline.chat;

import com.nefodov.oneline.support.NotFoundException;
import com.nefodov.oneline.support.TokenGenerator;
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
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;

    @Transactional
    public CreatedChat create() {
        String chatToken = tokenGenerator.newToken();
        Chat chat = new Chat();
        chat.setPublicId(UUID.randomUUID());
        chat.setChatTokenHash(tokenHasher.hash(chatToken));
        Chat saved = chatRepository.save(chat);
        return new CreatedChat(saved, chatToken);
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

    public record CreatedChat(Chat chat, String chatToken) {
    }
}
