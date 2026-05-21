package com.nefodov.oneline.chat;

import com.nefodov.oneline.support.OneLineProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

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
