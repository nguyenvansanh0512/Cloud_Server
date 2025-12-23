package com.example.CloudServer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Data
public class FileInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Column(name = "size_bytes")
    private Long size;

    @Column(name = "upload_date")
    private LocalDateTime uploadDate = LocalDateTime.now();

    @Column(name = "folder_id")
    private Long directoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ✅ THÊM CỘT MỚI: Đánh dấu file đang ở thùng rác
    @Column(name = "in_trash", nullable = false)
    private boolean inTrash = false;

    @Column(name = "is_starred")
    private boolean isStarred = false;

    private String type;
}