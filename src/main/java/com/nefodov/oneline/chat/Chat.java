package com.nefodov.oneline.chat;

import com.nefodov.oneline.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chats")
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accessToken;

    @ManyToOne(optional = false)
    private User inviter;

    @ManyToOne(optional = false)
    private User invitee;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

