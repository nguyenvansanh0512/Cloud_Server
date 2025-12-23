package com.example.CloudServer.repository;

import com.example.CloudServer.model.Directory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DirectoryRepository extends JpaRepository<Directory, Long> {

    // ✅ QUERY MỚI: Chỉ lấy folder chưa bị xóa
    @Query("SELECT d FROM Directory d WHERE d.userId = :userId AND d.parentDirectoryId = :parentId " +
            "AND d.id NOT IN (SELECT t.folder.id FROM Trash t WHERE t.folder.id IS NOT NULL)")
    List<Directory> findByUserIdAndParentDirectoryId(Long userId, Long parentId);

    @Query("SELECT d FROM Directory d WHERE d.userId = :userId AND d.parentDirectoryId IS NULL " +
            "AND d.id NOT IN (SELECT t.folder.id FROM Trash t WHERE t.folder.id IS NOT NULL)")
    List<Directory> findByUserIdAndParentDirectoryIdIsNull(Long userId);

    List<Directory> findAllByParentDirectoryId(Long parentId);

    @Query("SELECT d FROM Directory d WHERE d.userId = :userId AND d.inTrash = false AND LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Directory> searchDirectories(Long userId, String keyword);
}