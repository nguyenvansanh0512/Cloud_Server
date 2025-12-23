package com.example.CloudServer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "account") // Sửa: map vào bảng 'account' trong SQL
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") // Sửa: khớp SQL
    private Long id;

    @Column(nullable = false, unique = true)
    private String email; // SQL dùng email làm định danh chính

    @Column(name = "first_name")
    private String username; // Dùng first_name làm username hiển thị

    @Column(nullable = false)
    private String password;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}