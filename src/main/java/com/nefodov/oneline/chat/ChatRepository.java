package com.nefodov.oneline.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    Optional<Chat> findByPublicId(UUID publicId);

    @Modifying
    @Query("""
            DELETE FROM Chat c
            WHERE c.createdAt < :cutoff
            AND NOT EXISTS (SELECT 1 FROM Message m WHERE m.chat = c AND m.createdAt > :cutoff)
            """)
    int deleteInactiveBefore(@Param("cutoff") Instant cutoff);
}
