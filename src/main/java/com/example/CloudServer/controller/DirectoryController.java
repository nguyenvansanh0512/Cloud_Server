package com.example.CloudServer.controller;

import com.example.CloudServer.model.Directory;
import com.example.CloudServer.model.FileInfo;
import com.example.CloudServer.model.Trash;
import com.example.CloudServer.model.User;
import com.example.CloudServer.repository.*;
import com.example.CloudServer.service.FileStorageService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/directories")
public class DirectoryController {

    @Autowired private DirectoryRepository directoryRepository;
    @Autowired private FileInfoRepository fileInfoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TrashRepository trashRepository;
    @Autowired private ShareRepository shareRepository;
    @Autowired private FileStorageService fileStorageService; // Cần để lấy file vật lý khi nén Zip

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    // DTO trả về danh sách file và folder
    @Data
    static class DirectoryContentResponse {
        List<Directory> directories;
        List<FileInfo> files;
        public DirectoryContentResponse(List<Directory> directories, List<FileInfo> files) {
            this.directories = directories;
            this.files = files;
        }
    }

    // --- 1. HÀM KIỂM TRA QUYỀN (ĐỆ QUY NGƯỢC) ---
    // Kiểm tra xem userId có quyền truy cập folderId hay không (Do là chủ sở hữu hoặc được share)
    private boolean canAccessFolder(Long folderId, Long userId) {
        if (folderId == null) return true; // Root của chính mình luôn OK

        Long currentId = folderId;
        // Vòng lặp leo ngược lên cây thư mục
        while (currentId != null) {
            Directory dir = directoryRepository.findById(currentId).orElse(null);
            if (dir == null) return false;

            // 1. Nếu là chủ sở hữu -> OK
            if (dir.getUserId().equals(userId)) return true;

            // 2. Nếu thư mục này được share trực tiếp cho user -> OK
            if (shareRepository.existsByReceiverIdAndFolderId(userId, currentId)) return true;

            // 3. Leo lên thư mục cha để kiểm tra tiếp
            currentId = dir.getParentDirectoryId();
        }
        return false;
    }

    // --- 2. API LẤY NỘI DUNG THƯ MỤC ---
    @GetMapping("/content")
    public ResponseEntity<?> getDirectoryContent(@RequestParam(value = "directoryId", required = false) Long directoryId) {
        try {
            User user = getCurrentUser();

            // A. Nếu là thư mục gốc (directoryId == null) -> Lấy của chính user
            if (directoryId == null) {
                List<Directory> dirs = directoryRepository.findByUserIdAndParentDirectoryIdIsNull(user.getId());
                List<FileInfo> files = fileInfoRepository.findByUserIdAndDirectoryIdIsNullAndInTrashFalse(user.getId());
                return ResponseEntity.ok(new DirectoryContentResponse(dirs, files));
            }

            // B. Nếu là thư mục con -> PHẢI CHECK QUYỀN
            if (!canAccessFolder(directoryId, user.getId())) {
                return ResponseEntity.status(403).body("Bạn không có quyền truy cập thư mục này.");
            }

            // C. Lấy nội dung (bất kể chủ sở hữu là ai, vì đã check quyền rồi)
            // Lọc bỏ các file/folder đang ở trong thùng rác
            List<Directory> allDirs = directoryRepository.findAllByParentDirectoryId(directoryId);
            List<Directory> visibleDirs = allDirs.stream().filter(d -> !d.isInTrash()).toList();

            List<FileInfo> allFiles = fileInfoRepository.findAllByDirectoryId(directoryId);
            List<FileInfo> visibleFiles = allFiles.stream().filter(f -> !f.isInTrash()).toList();

            return ResponseEntity.ok(new DirectoryContentResponse(visibleDirs, visibleFiles));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // --- 3. API TẠO THƯ MỤC ---
    @PostMapping("/create")
    public ResponseEntity<?> createDirectory(@RequestBody Map<String, Object> payload) {
        try {
            User user = getCurrentUser();
            String name = (String) payload.get("name");
            Long parentId = payload.get("parentDirectoryId") != null ? ((Number) payload.get("parentDirectoryId")).longValue() : null;

            Directory dir = new Directory();
            dir.setName(name);
            dir.setUserId(user.getId());
            dir.setParentDirectoryId(parentId);

            return ResponseEntity.ok(directoryRepository.save(dir));
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // --- 4. API TẢI FOLDER (Nén ZIP) ---
    @GetMapping("/download/{folderId}")
    public ResponseEntity<Resource> downloadFolder(@PathVariable Long folderId) {
        try {
            User user = getCurrentUser();

            // Kiểm tra quyền truy cập trước khi cho tải
            if (!canAccessFolder(folderId, user.getId())) {
                return ResponseEntity.status(403).build();
            }

            Directory rootDir = directoryRepository.findById(folderId).orElseThrow();

            // Tạo luồng byte để nén zip
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            // Gọi hàm đệ quy để nén toàn bộ
            zipDirectory(rootDir, rootDir.getName(), zos);

            zos.close();

            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rootDir.getName() + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    // --- 5. API XÓA THƯ MỤC (SOFT DELETE) ---
    @DeleteMapping("/{folderId}")
    public ResponseEntity<?> deleteFolder(@PathVariable Long folderId) {
        try {
            User user = getCurrentUser();
            Directory dir = directoryRepository.findById(folderId).orElseThrow();

            // Chỉ chủ sở hữu mới được xóa
            if (!dir.getUserId().equals(user.getId())) {
                return ResponseEntity.status(403).body("Bạn không có quyền xóa thư mục này.");
            }

            // 1. Xóa mềm đệ quy (ẩn con cháu)
            softDeleteRecursive(dir);

            // 2. Tạo bản ghi Trash cho folder cha
            boolean alreadyInTrash = trashRepository.findByFolderId(folderId).isPresent();
            if (!alreadyInTrash) {
                Trash trash = new Trash(user.getId(), null, dir);
                trashRepository.save(trash);
            }

            return ResponseEntity.ok("Đã chuyển thư mục và nội dung vào thùng rác");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi: " + e.getMessage());
        }
    }

    // ================== CÁC HÀM HỖ TRỢ (PRIVATE) ==================

    // Hỗ trợ xóa mềm đệ quy
    private void softDeleteRecursive(Directory dir) {
        // A. Ẩn folder hiện tại
        dir.setInTrash(true);
        directoryRepository.save(dir);

        // B. Ẩn tất cả FILE trong folder này
        List<FileInfo> files = fileInfoRepository.findAllByDirectoryId(dir.getId());
        for (FileInfo f : files) {
            f.setInTrash(true);
            fileInfoRepository.save(f);
        }

        // C. Ẩn (đệ quy) tất cả FOLDER CON
        List<Directory> subFolders = directoryRepository.findAllByParentDirectoryId(dir.getId());
        for (Directory sub : subFolders) {
            softDeleteRecursive(sub);
        }
    }

    // Hỗ trợ nén ZIP đệ quy
    private void zipDirectory(Directory dir, String parentPath, ZipOutputStream zos) throws IOException {
        // A. Nén tất cả FILE trong folder hiện tại
        List<FileInfo> files = fileInfoRepository.findAllByDirectoryId(dir.getId());
        for (FileInfo file : files) {
            if (file.isInTrash()) continue;

            try {
                Resource resource = fileStorageService.loadFileAsResource(file.getStoredFilename());
                File physicalFile = resource.getFile();

                if (physicalFile.exists()) {
                    String zipEntryName = parentPath + "/" + file.getOriginalFilename();
                    zos.putNextEntry(new ZipEntry(zipEntryName));

                    FileInputStream fis = new FileInputStream(physicalFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    fis.close();
                    zos.closeEntry();
                }
            } catch (Exception e) {
                System.err.println("Lỗi nén file: " + file.getOriginalFilename());
            }
        }

        // B. Tiếp tục đệ quy với các FOLDER CON
        List<Directory> subDirs = directoryRepository.findAllByParentDirectoryId(dir.getId());
        for (Directory subDir : subDirs) {
            if (subDir.isInTrash()) continue;
            zipDirectory(subDir, parentPath + "/" + subDir.getName(), zos);
        }
    }
}