package com.nefodov.oneline.chat;

import com.nefodov.oneline.support.OneLineProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Periodically removes chats that have been silent for {@code oneline.retention.inactivity-window}.
 * A chat counts as inactive if it was created before the cutoff <em>and</em> has no messages newer
 * than the cutoff. Deletion cascades to participants and messages via FKs.
 *
 * <p>The cron expression is interpreted in {@code oneline.retention.zone}, not the JVM default,
 * so we can keep the rest of the system in UTC and still fire the job at a sensible local hour.
 */
@Slf4j
@Component
@AllArgsConstructor
public class ChatLifecycleScheduler {

    private final ChatService chatService;
    private final OneLineProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${oneline.retention.cron}", zone = "${oneline.retention.zone}")
    public void deleteInactiveChats() {
        Instant cutoff = clock.instant().minus(properties.retention().inactivityWindow());
        int deleted = chatService.deleteInactiveBefore(cutoff);
        if (deleted > 0) {
            log.info("Removed {} inactive chat(s) older than {}", deleted, cutoff);
        }
    }
}
