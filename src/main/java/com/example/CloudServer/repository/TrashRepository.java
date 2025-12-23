package com.example.CloudServer.repository;

import com.example.CloudServer.model.Trash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrashRepository extends JpaRepository<Trash, Long> {

    // Lấy danh sách thùng rác của user (để hiển thị)
    List<Trash> findByUserIdOrderByDeletedAtDesc(Long userId);

    // Kiểm tra xem file/folder đã có trong thùng rác chưa (để tránh lỗi)
    Optional<Trash> findByFileId(Long fileId);
    Optional<Trash> findByFolderId(Long folderId);

    // ✅ THÊM HÀM NÀY ĐỂ CHECK NHANH (Trả về true/false)
    // Spring Data JPA sẽ tự tạo câu lệnh SQL: SELECT COUNT(*) > 0 ...
    boolean existsByUserIdAndFileId(Long userId, Long fileId);

    void deleteByFileId(Long fileId);
    void deleteByFolderId(Long folderId);
}