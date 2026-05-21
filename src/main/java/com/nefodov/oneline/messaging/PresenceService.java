package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.dto.ParticipantView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PresenceService {

    private static final String KEY_PREFIX = "oneline:presence:";

    private final StringRedisTemplate redis;

    public PresenceService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void markOnline(Long chatId, Long participantId, String displayName) {
        redis.opsForHash().put(key(chatId), String.valueOf(participantId), displayName);
    }

    public void markOffline(Long chatId, Long participantId) {
        redis.opsForHash().delete(key(chatId), String.valueOf(participantId));
    }

    public List<ParticipantView> online(Long chatId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(chatId));
        List<ParticipantView> participants = new ArrayList<>(entries.size());
        entries.forEach((id, name) -> participants.add(new ParticipantView(Long.valueOf((String) id), (String) name)));
        return participants;
    }

    private static String key(Long chatId) {
        return KEY_PREFIX + chatId;
    }
}
