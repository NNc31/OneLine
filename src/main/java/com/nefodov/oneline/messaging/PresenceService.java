package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.dto.ParticipantView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PresenceService {

    private static final String LIVENESS_PREFIX = "oneline:presence:z:";
    private static final String NAMES_PREFIX = "oneline:presence:n:";
    private static final Duration STALE_AFTER = Duration.ofSeconds(60);
    private static final Duration KEY_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public PresenceService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void markOnline(Long chatId, Long participantId, String displayName) {
        String member = String.valueOf(participantId);
        redis.opsForZSet().add(livenessKey(chatId), member, clock.millis());
        redis.opsForHash().put(namesKey(chatId), member, displayName);
        redis.expire(livenessKey(chatId), KEY_TTL);
        redis.expire(namesKey(chatId), KEY_TTL);
    }

    public void markOffline(Long chatId, Long participantId) {
        String member = String.valueOf(participantId);
        redis.opsForZSet().remove(livenessKey(chatId), member);
        redis.opsForHash().delete(namesKey(chatId), member);
    }

    public int evictStale(Long chatId) {
        long cutoff = clock.millis() - STALE_AFTER.toMillis();
        Set<String> stale = redis.opsForZSet().rangeByScore(livenessKey(chatId), 0, cutoff);
        if (stale == null || stale.isEmpty()) {
            return 0;
        }
        Object[] members = stale.toArray();
        redis.opsForZSet().remove(livenessKey(chatId), members);
        redis.opsForHash().delete(namesKey(chatId), members);
        return stale.size();
    }

    public List<ParticipantView> online(Long chatId) {
        evictStale(chatId);
        Set<String> ids = redis.opsForZSet().range(livenessKey(chatId), 0, -1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ParticipantView> participants = new ArrayList<>(ids.size());
        for (String id : ids) {
            Object name = redis.opsForHash().get(namesKey(chatId), id);
            if (name != null) {
                participants.add(new ParticipantView(Long.valueOf(id), (String) name));
            }
        }
        return participants;
    }

    private static String livenessKey(Long chatId) {
        return LIVENESS_PREFIX + chatId;
    }

    private static String namesKey(Long chatId) {
        return NAMES_PREFIX + chatId;
    }
}
