package com.example.CloudServer.controller;

import com.example.CloudServer.model.*;
import com.example.CloudServer.repository.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    // Trong file com/example/CloudServer/controller/TrashController.java

    @PostMapping("/restore/{id}")
    @Transactional // <--- Thêm cái này để đảm bảo toàn vẹn dữ liệu
    public ResponseEntity<String> restoreItem(@PathVariable Long id) {
        // 1. Tìm mục trong thùng rác
        Trash trash = trashRepository.findById(id).orElse(null);
        if (trash == null) return ResponseEntity.notFound().build();

        // 2. Nếu là FILE
        if (trash.getFile() != null) {
            FileInfo file = trash.getFile();

            // --- ĐOẠN CODE BẠN BỊ THIẾU ---
            file.setInTrash(false);         // Đánh dấu là không còn ở trong rác
            fileInfoRepository.save(file);  // Lưu lại vào DB
            // ------------------------------
        }
        // 3. Nếu là FOLDER
        else if (trash.getFolder() != null) {
            Directory folder = trash.getFolder();

            // --- ĐOẠN CODE BẠN BỊ THIẾU ---
            folder.setInTrash(false);           // Cần thêm trường inTrash cho Directory nếu chưa có logic tương tự
            directoryRepository.save(folder);   // Lưu lại
            // ------------------------------
        }

        // 4. Xóa khỏi bảng Trash
        trashRepository.delete(trash);

        return ResponseEntity.ok("Khôi phục thành công");
    }

    // 3. Xóa vĩnh viễn (Delete Forever)
    @DeleteMapping("/delete/{id}")
    @Transactional // <--- QUAN TRỌNG: Bắt buộc có để thực hiện xóa DB
    public ResponseEntity<String> deleteForever(@PathVariable Long id) {
        Trash trash = trashRepository.findById(id).orElse(null);
        if (trash == null) return ResponseEntity.notFound().build();

        if (trash.getFile() != null) {
            // Gọi hàm xóa file (đã sửa ở dưới)
            deleteSingleFile(trash.getFile());
        } else if (trash.getFolder() != null) {
            // Gọi hàm xóa folder
            deleteFolderRecursively(trash.getFolder());
        }

        // Sau khi xóa file/folder xong thì record trong Trash tự động mất
        // (hoặc delete thủ công nếu logic deleteSingleFile chưa xử lý Trash)
        if (trashRepository.existsById(id)) {
            trashRepository.delete(trash);
        }

        return ResponseEntity.ok("Đã xóa vĩnh viễn");
    }

    // HÀM PHỤ TRỢ (Sửa lại logic)
    private void deleteSingleFile(FileInfo file) {
        try {
            // 1. Xóa file vật lý
            Path root = Paths.get("uploads");
            Path filePath = root.resolve(file.getStoredFilename());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Lỗi xóa file vật lý: " + e.getMessage());
        }

        // 2. Xóa liên kết trong Trash TRƯỚC (QUAN TRỌNG)
        // Dùng deleteBy... là cách an toàn nhất để gỡ bỏ khóa ngoại
        if (trashRepository.existsByUserIdAndFileId(file.getUserId(), file.getId())) {
            trashRepository.deleteByFileId(file.getId());
        }

        // 3. Xóa thông tin file
        fileInfoRepository.delete(file);
    }

    @DeleteMapping("/delete-all")
    @Transactional
    public ResponseEntity<String> deleteAllTrash() {
        try {
            User user = getCurrentUser();
            List<Trash> trashList = trashRepository.findByUserIdOrderByDeletedAtDesc(user.getId());

            for (Trash trash : trashList) {
                // Chỉ gọi hàm xóa file/folder, KHÔNG can thiệp thủ công vào trashRepository ở đây nữa
                // vì bên trong các hàm deleteSingleFile/deleteFolderRecursively đã tự xử lý Trash rồi.
                if (trash.getFile() != null) {
                    deleteSingleFile(trash.getFile());
                } else if (trash.getFolder() != null) {
                    deleteFolderRecursively(trash.getFolder());
                }
            }

            // Bước chốt: Xóa sạch những gì còn sót lại trong Trash của user này (nếu có lỗi logic nào đó bỏ sót)
            // Lưu ý: Cần đảm bảo trong TrashRepository có hàm void deleteByUserId(Long userId);
            // trashRepository.deleteByUserId(user.getId());

            return ResponseEntity.ok("Đã dọn sạch thùng rác");
        } catch (Exception e) {
            e.printStackTrace(); // In lỗi ra console để bạn dễ debug
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi: " + e.getMessage());
        }
    }

    // Hàm 2: Đệ quy xóa Folder (File con + Folder con)
    private void deleteFolderRecursively(Directory folder) {
        // A. Xóa FILE con
        List<FileInfo> files = fileInfoRepository.findAllByDirectoryId(folder.getId());
        for (FileInfo file : files) {
            deleteSingleFile(file);
        }

        // B. Xóa FOLDER con
        List<Directory> subFolders = directoryRepository.findAllByParentDirectoryId(folder.getId());
        for (Directory subFolder : subFolders) {
            deleteFolderRecursively(subFolder);
        }

        // C. Xóa Trash liên quan đến folder này
        if (trashRepository.findByFolderId(folder.getId()).isPresent()) {
            trashRepository.deleteByFolderId(folder.getId());
        }

        // D. Xóa Folder
        directoryRepository.delete(folder);
    }

}