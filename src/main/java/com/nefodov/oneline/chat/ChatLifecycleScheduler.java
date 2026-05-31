package com.nefodov.oneline.chat;

import com.nefodov.oneline.attachment.AttachmentCleanupService;
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
    private final AttachmentCleanupService attachmentCleanupService;
    private final OneLineProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${oneline.retention.cron}", zone = "${oneline.retention.zone}")
    public void deleteInactiveChats() {
        Instant cutoff = clock.instant().minus(properties.retention().inactivityWindow());
        int deletedObjects = attachmentCleanupService.removeObjectsForInactiveChats(cutoff);
        int deletedChats = chatService.deleteInactiveBefore(cutoff);
        if (deletedChats > 0) {
            log.info("Removed {} inactive chat(s) older than {} ({} attachment object(s) purged)", deletedChats, cutoff, deletedObjects);
        }
    }
}
