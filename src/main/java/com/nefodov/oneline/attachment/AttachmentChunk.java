package com.nefodov.oneline.attachment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attachment_chunks", uniqueConstraints = @UniqueConstraint(name = "uq_attachment_chunks_index", columnNames = {"attachment_id", "chunk_index"}))
@Getter
@Setter
@NoArgsConstructor
public class AttachmentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    @Column(name = "chunk_index", nullable = false, updatable = false)
    private int chunkIndex;

    @Column(name = "object_key", nullable = false, unique = true, updatable = false)
    private String objectKey;

    @Column(name = "ciphertext_size", nullable = false)
    private long ciphertextSize;
}
