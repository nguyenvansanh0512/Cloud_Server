package com.example.CloudServer.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "trash")
@Data
@NoArgsConstructor
public class Trash {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trash_id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    // Liên kết với File (Có thể null nếu xóa Folder)
    @OneToOne
    @JoinColumn(name = "file_id")
    private FileInfo file;

    // Liên kết với Folder (Có thể null nếu xóa File)
    @OneToOne
    @JoinColumn(name = "folder_id")
    private Directory folder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = LocalDateTime.now();

    // Constructor tiện lợi
    public Trash(Long userId, FileInfo file, Directory folder) {
        this.userId = userId;
        this.file = file;
        this.folder = folder;
    }
}