package com.example.CloudServer.repository;

import com.example.CloudServer.model.FileInfo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {

    // 1. Tính dung lượng ACTIVE (File chưa xóa -> inTrash = false)
    @Query("SELECT SUM(f.size) FROM FileInfo f WHERE f.userId = :userId AND f.inTrash = false")
    Long sumActiveSizeByUserId(Long userId);

    // 2. Tính dung lượng TRASH (File đã xóa -> inTrash = true)
    @Query("SELECT SUM(f.size) FROM FileInfo f JOIN Trash t ON f.id = t.file.id WHERE f.userId = :userId")
    Long sumTrashSizeByUserId(Long userId);

    // 3. Tính Transfer hôm nay (Không quan tâm xóa hay chưa, cứ upload là tính)
    @Query("SELECT SUM(f.size) FROM FileInfo f WHERE f.userId = :userId AND f.uploadDate >= :startOfDay")
    Long sumTransferToday(Long userId, LocalDateTime startOfDay);

    // 4. Lấy danh sách file hiển thị (Chỉ lấy file chưa xóa)
    List<FileInfo> findByUserIdAndDirectoryIdAndInTrashFalse(Long userId, Long directoryId);
    List<FileInfo> findByUserIdAndDirectoryIdIsNullAndInTrashFalse(Long userId); // Lấy file ở thư mục gốc

    // 5. Lấy file gần đây (Chỉ lấy file chưa xóa)
    @Query("SELECT f FROM FileInfo f WHERE f.userId = :userId AND f.inTrash = false ORDER BY f.uploadDate DESC")
    List<FileInfo> findRecentFiles(Long userId, Pageable pageable);

    // 6. Lấy danh sách file TRONG THÙNG RÁC để hiển thị
    List<FileInfo> findByUserIdAndInTrashTrue(Long userId);

    List<FileInfo> findAllByDirectoryId(Long directoryId);

    List<FileInfo> findByUserIdAndIsStarredTrueAndInTrashFalse(Long userId);

    @Query("SELECT f FROM FileInfo f WHERE f.userId = :userId AND f.inTrash = false AND LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<FileInfo> searchFilesByUserIdAndName(Long userId, String keyword);
}