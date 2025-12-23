package com.example.CloudServer.controller;

import com.example.CloudServer.model.*;
import com.example.CloudServer.repository.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

@RestController
@RequestMapping("/api/trash")
public class TrashController {

    @Autowired TrashRepository trashRepository;
    @Autowired UserRepository userRepository;
    @Autowired FileInfoRepository fileInfoRepository;
    @Autowired DirectoryRepository directoryRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    // DTO trả về cho Client
    @Data
    static class TrashResponse {
        private Long trashId;
        private String itemName;
        private String itemType; // FILE / FOLDER
        private String deletedDate;

        public TrashResponse(Long trashId, String itemName, String itemType, String deletedDate) {
            this.trashId = trashId;
            this.itemName = itemName;
            this.itemType = itemType;
            this.deletedDate = deletedDate;
        }
    }

    // 1. Lấy danh sách thùng rác
    @GetMapping
    public List<TrashResponse> getTrashItems() {
        User user = getCurrentUser();
        return trashRepository.findByUserIdOrderByDeletedAtDesc(user.getId()).stream()
                .map(t -> new TrashResponse(
                        t.getId(),
                        t.getFile() != null ? t.getFile().getOriginalFilename() : t.getFolder().getName(),
                        t.getFile() != null ? "FILE" : "FOLDER",
                        t.getDeletedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                ))
                .collect(Collectors.toList());
    }

    // 2. Khôi phục (Restore) - Chỉ cần xóa dòng trong bảng TRASH là file tự hiện lại bên All File
    @PostMapping("/restore/{trashId}")
    public ResponseEntity<?> restoreItem(@PathVariable Long trashId) {
        try {
            Trash trash = trashRepository.findById(trashId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            // Xóa khỏi bảng Trash -> File sẽ không bị chặn bởi Query "NOT IN TRASH" nữa
            trashRepository.delete(trash);

            return ResponseEntity.ok("Đã khôi phục thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // 3. Xóa vĩnh viễn (Delete Forever)
    @DeleteMapping("/{trashId}")
    @Transactional // ⚠️ Quan trọng: Để xóa đồng bộ, lỗi là hoàn tác hết
    public ResponseEntity<?> deleteForever(@PathVariable Long trashId) {
        try {
            Trash trash = trashRepository.findById(trashId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            // 1. Lưu tham chiếu
            FileInfo fileToDelete = trash.getFile();
            Directory folderToDelete = trash.getFolder();

            // 2. Xóa bản ghi trong Trash trước để gỡ khóa ngoại
            trashRepository.delete(trash);

            // 3. Xóa dữ liệu gốc
            if (fileToDelete != null) {
                deleteSingleFile(fileToDelete);
            } else if (folderToDelete != null) {
                // Gọi đệ quy xóa folder
                deleteFolderRecursively(folderToDelete);
            }

            return ResponseEntity.ok("Đã xóa vĩnh viễn");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // --- CÁC HÀM PHỤ TRỢ (PRIVATE) ---

    // Hàm 1: Xóa 1 file (Vật lý + DB)
    private void deleteSingleFile(FileInfo file) {
        try {
            // A. Xóa file vật lý (Sửa đường dẫn "uploads" theo server của bạn)
            Path root = Paths.get("uploads");
            Path filePath = root.resolve(file.getStoredFilename());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Lỗi xóa file vật lý: " + file.getOriginalFilename());
        }

        // B. Xóa trong Trash nếu nó đang nằm đó
        trashRepository.deleteByFileId(file.getId());

        // C. Xóa trong bảng Files
        fileInfoRepository.delete(file);
    }

    // Hàm 2: Đệ quy xóa Folder (File con + Folder con)
    private void deleteFolderRecursively(Directory folder) {
        // A. Xóa tất cả FILE trong folder này
        List<FileInfo> files = fileInfoRepository.findAllByDirectoryId(folder.getId());
        for (FileInfo file : files) {
            deleteSingleFile(file);
        }

        // B. Xóa tất cả FOLDER CON (Gọi lại chính nó)
        List<Directory> subFolders = directoryRepository.findAllByParentDirectoryId(folder.getId());
        for (Directory subFolder : subFolders) {
            deleteFolderRecursively(subFolder);
        }

        // C. Xóa chính folder này
        trashRepository.deleteByFolderId(folder.getId());
        directoryRepository.delete(folder);
    }

}