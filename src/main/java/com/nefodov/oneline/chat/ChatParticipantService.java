package com.nefodov.oneline.chat;

import com.nefodov.oneline.support.ConflictException;
import com.nefodov.oneline.support.OneLineProperties;
import com.nefodov.oneline.support.TokenGenerator;
import com.nefodov.oneline.support.TokenHasher;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ChatParticipantService {

    private final ChatParticipantRepository participantRepository;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final OneLineProperties properties;
    private final Clock clock;

    @Transactional
    public JoinedParticipant join(Chat chat, String displayName) {
        String normalizedName = normalize(displayName);
        Instant activeSince = clock.instant().minus(properties.participant().activityWindow());
        if (participantRepository.existsByChatIdAndDisplayNameAndLastSeenAtAfter(chat.getId(), normalizedName, activeSince)) {
            throw new ConflictException("Display name is taken by an active participant");
        }
        String sessionToken = tokenGenerator.newToken();
        ChatParticipant participant = new ChatParticipant();
        participant.setChat(chat);
        participant.setSessionTokenHash(tokenHasher.hash(sessionToken));
        participant.setDisplayName(normalizedName);
        ChatParticipant saved = participantRepository.save(participant);
        return new JoinedParticipant(saved, sessionToken);
    }

    @Transactional(readOnly = true)
    public Optional<ChatParticipant> resolveBySession(String sessionToken) {
        return participantRepository.findBySessionTokenHash(tokenHasher.hash(sessionToken));
    }

    @Transactional(readOnly = true)
    public long countParticipants(Chat chat) {
        return participantRepository.countByChatId(chat.getId());
    }

    @Transactional
    public void touch(ChatParticipant participant) {
        participantRepository.touchLastSeen(participant.getId(), clock.instant());
    }

    private String normalize(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Display name is required");
        }
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (trimmed.length() > 40) {
            throw new IllegalArgumentException("Display name is too long");
        }
        return trimmed;
    }

    public record JoinedParticipant(ChatParticipant participant, String sessionToken) {
    }
}
