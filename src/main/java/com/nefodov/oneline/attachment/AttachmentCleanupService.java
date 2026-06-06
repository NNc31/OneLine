package com.nefodov.oneline.attachment;

import com.nefodov.oneline.config.OneLineProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@AllArgsConstructor
public class AttachmentCleanupService {

    private final AttachmentRepository repository;
    private final AttachmentStorage storage;
    private final OneLineProperties properties;
    private final Clock clock;

    public int sweepExpiredByChatTtl() {
        return removeFull(repository.findExpiredAttachmentIdsByChatTtl());
    }

    public int sweepUnconfirmed() {
        Instant cutoff = clock.instant().minus(properties.storage().unconfirmedTtl());
        return removeFull(repository.findUnconfirmedAttachmentIdsOlderThan(cutoff));
    }

    public int sweepExpiredByAttachmentTtl() {
        Duration ttl = properties.attachments().ttl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(ttl);
        return removeFull(repository.findAttachmentIdsOlderThan(cutoff));
    }

    public int removeObjectsForInactiveChats(Instant cutoff) {
        List<Long> ids = repository.findAttachmentIdsForInactiveChatsBefore(cutoff);
        if (ids.isEmpty()) {
            return 0;
        }
        List<String> keys = repository.findAllObjectKeysByAttachmentIds(ids);
        if (!keys.isEmpty()) {
            storage.remove(keys);
        }
        return ids.size();
    }

    private int removeFull(List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return 0;
        }
        List<String> keys = repository.findAllObjectKeysByAttachmentIds(attachmentIds);
        if (!keys.isEmpty()) {
            storage.remove(keys);
        }
        return repository.deleteByIds(attachmentIds);
    }
}
