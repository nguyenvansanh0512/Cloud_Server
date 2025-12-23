package com.example.CloudServer.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "shares")
@Data
@NoArgsConstructor
public class Share {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // QUAN TRỌNG: nullable = true (Để khi share folder thì file có thể null)
    @ManyToOne
    @JoinColumn(name = "file_id", nullable = true)
    private FileInfo file;

    // QUAN TRỌNG: nullable = true (Để khi share file thì folder có thể null)
    @ManyToOne
    @JoinColumn(name = "folder_id", nullable = true)
    private Directory folder;

    @Column(name = "shared_at")
    private LocalDateTime sharedAt = LocalDateTime.now();

    private String permission = "VIEW";
}