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
        int expired = cleanupService.sweepExpiredByChatTtl();
        int unconfirmed = cleanupService.sweepUnconfirmed();
        if (expired > 0 || unconfirmed > 0) {
            log.info("Swept {} expired and {} unconfirmed attachment(s)", expired, unconfirmed);
        }
    }
}
