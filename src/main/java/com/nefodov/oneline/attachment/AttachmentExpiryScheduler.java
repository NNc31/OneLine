package com.nefodov.oneline.attachment;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class AttachmentExpiryScheduler {

    private final AttachmentCleanupService cleanupService;

    @Scheduled(fixedDelay = 60_000L)
    public void sweep() {
        int expiredByChat = cleanupService.sweepExpiredByChatTtl();
        int unconfirmed = cleanupService.sweepUnconfirmed();
        int expiredByTtl = cleanupService.sweepExpiredByAttachmentTtl();
        if (expiredByChat > 0 || unconfirmed > 0 || expiredByTtl > 0) {
            log.info("Swept {} chat-TTL-expired, {} unconfirmed, {} attachment-TTL-expired attachment(s)", expiredByChat, unconfirmed, expiredByTtl);
        }
    }
}
