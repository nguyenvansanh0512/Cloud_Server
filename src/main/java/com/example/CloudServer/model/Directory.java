package com.example.CloudServer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "folders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Directory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id")
    private Long id;

    @Column(name = "folder_name", nullable = false)
    private String name;

    @Column(name = "parent_folder_id")
    private Long parentDirectoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ✅ THÊM MỚI: Khai báo cột in_trash và gán mặc định là false
    @Column(name = "in_trash", nullable = false, columnDefinition = "boolean default false")
    private boolean inTrash = false;

    // Constructor tiện ích (Sửa lại để đảm bảo inTrash luôn có giá trị)
    public Directory(String name, Long parentDirectoryId, Long userId) {
        this.name = name;
        this.parentDirectoryId = parentDirectoryId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
        this.inTrash = false; // ✅ Quan trọng: Gán giá trị mặc định khi tạo mới
    }
}