package com.nefodov.oneline.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "chat_token_hash", nullable = false, unique = true)
    private byte[] chatTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "message_ttl_seconds")
    private Long messageTtlSeconds;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isClosed() {
        return closedAt != null;
    }
}
