package com.nefodov.oneline.attachment;

import com.nefodov.oneline.support.OneLineProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
        return removeInFull(repository.findExpiredObjectKeysByChatTtl());
    }

    public int sweepUnconfirmed() {
        Instant cutoff = clock.instant().minus(properties.storage().unconfirmedTtl());
        return removeInFull(repository.findUnconfirmedObjectKeysOlderThan(cutoff));
    }

    public int removeObjectsForInactiveChats(Instant cutoff) {
        List<String> keys = repository.findObjectKeysForInactiveChatsBefore(cutoff);
        if (keys.isEmpty()) {
            return 0;
        }
        storage.remove(keys);
        return keys.size();
    }

    private int removeInFull(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return 0;
        }
        storage.remove(objectKeys);
        return repository.deleteByObjectKeys(objectKeys);
    }
}
