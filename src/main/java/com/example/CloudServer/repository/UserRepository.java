package com.example.CloudServer.repository;

import com.example.CloudServer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Tìm người dùng bằng email (Dùng cho chức năng Đăng nhập)
    Optional<User> findByEmail(String email);

    // Kiểm tra email đã tồn tại chưa (Dùng cho chức năng Đăng ký)
    Boolean existsByEmail(String email);

    // Tìm theo tên hiển thị (nếu cần hiển thị profile)
    // Lưu ý: Trong User Entity, 'username' đang map vào cột 'first_name'
    Optional<User> findByUsername(String username);
}