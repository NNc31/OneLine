package com.nefodov.oneline.message;

import com.nefodov.oneline.chat.Chat;
import com.nefodov.oneline.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Chat chat;

    @ManyToOne(optional = false)
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
