package com.nefodov.oneline.message;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class MessageExpiryScheduler {

    private final MessageService messageService;

    @Scheduled(fixedDelay = 60_000L)
    public void cleanupExpiredMessages() {
        int deleted = messageService.deleteExpired();
        if (deleted > 0) {
            log.info("Swept {} expired message(s)", deleted);
        }
    }
}
