package com.nefodov.oneline.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class GracefulShutdownBroadcaster implements SmartLifecycle {

    public static final String SYSTEM_EVENTS_TOPIC = "/topic/system.events";
    private static final long NOTICE_DRAIN_MS = 500L;

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GracefulShutdownBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            log.info("Broadcasting shutdown notice to STOMP clients");
            messagingTemplate.convertAndSend(SYSTEM_EVENTS_TOPIC, (Object) Map.of("type", "shutdown"));
            Thread.sleep(NOTICE_DRAIN_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Failed to broadcast shutdown notice", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
