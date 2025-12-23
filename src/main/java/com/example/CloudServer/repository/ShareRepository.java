package com.example.CloudServer.repository;

import com.example.CloudServer.model.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {

    // Lấy danh sách được chia sẻ
    List<Share> findByReceiverIdOrderBySharedAtDesc(Long receiverId);

    // [MỚI] Kiểm tra xem user có được share Folder này không
    boolean existsByReceiverIdAndFolderId(Long receiverId, Long folderId);

    // [MỚI] Kiểm tra xem user có được share File này không
    boolean existsByReceiverIdAndFileId(Long receiverId, Long fileId);
}