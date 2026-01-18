package com.nefodov.oneline.chat;

import com.nefodov.oneline.support.NotFoundException;
import com.nefodov.oneline.support.TokenGenerator;
import com.nefodov.oneline.support.TokenHasher;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;

    @Transactional
    public CreatedChat create(String name) {
        String chatToken = tokenGenerator.newToken();
        Chat chat = new Chat();
        chat.setChatTokenHash(tokenHasher.hash(chatToken));
        chat.setName(name);
        Chat saved = chatRepository.save(chat);
        return new CreatedChat(saved, chatToken);
    }

    @Transactional(readOnly = true)
    public Chat findActive(String chatToken) {
        Chat chat = chatRepository.findByChatTokenHash(tokenHasher.hash(chatToken)).orElseThrow(() -> new NotFoundException("Chat not found"));
        if (chat.isClosed()) {
            throw new NotFoundException("Chat not found");
        }
        return chat;
    }

    public record CreatedChat(Chat chat, String chatToken) {
    }
}
