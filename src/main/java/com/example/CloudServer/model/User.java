package com.example.CloudServer.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
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

    @Column(name = "last_name")
    private String lastname; // Dùng first_name làm username hiển thị

    @Column(nullable = false)
    private String password;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth; // Dùng LocalDate thay vì LocalDateTime vì ngày sinh không cần giờ

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}