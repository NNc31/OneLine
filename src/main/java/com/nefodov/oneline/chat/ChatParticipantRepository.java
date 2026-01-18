package com.nefodov.oneline.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findBySessionTokenHash(byte[] sessionTokenHash);

    boolean existsByChatIdAndDisplayNameAndLastSeenAtAfter(
            Long chatId,
            String displayName,
            Instant lastSeenAfter
    );

    long countByChatId(Long chatId);

    @Modifying
    @Query("UPDATE ChatParticipant p SET p.lastSeenAt = :now WHERE p.id = :id")
    int touchLastSeen(@Param("id") Long id, @Param("now") Instant now);
}
