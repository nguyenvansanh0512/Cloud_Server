package com.example.CloudServer.controller;

import com.example.CloudServer.dto.DashboardMetricsDto;
import com.example.CloudServer.model.Directory;
import com.example.CloudServer.model.FileInfo;
import com.example.CloudServer.model.Trash;
import com.example.CloudServer.repository.*;
import com.example.CloudServer.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FileController {

    @Autowired private FileStorageService fileStorageService;
    @Autowired private FileInfoRepository fileInfoRepository;
    @Autowired private TrashRepository trashRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DirectoryRepository directoryRepository;
    @Autowired private ShareRepository shareRepository;

    // Hàm lấy User ID từ Token (An toàn nhất)
    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found")).getId();
    }

    // --- CHECK QUYỀN TRUY CẬP (Logic Share Đệ Quy) ---
    private boolean canAccessFile(FileInfo file, Long userId) {
        // 1. Chủ sở hữu -> OK
        if (file.getUserId().equals(userId)) return true;

        // 2. File được share trực tiếp -> OK
        if (shareRepository.existsByReceiverIdAndFileId(userId, file.getId())) return true;

        // 3. Nếu file nằm trong folder -> Check quyền folder cha (đệ quy)
        if (file.getDirectoryId() != null) {
            return checkFolderHierarchyAccess(file.getDirectoryId(), userId);
        }
        return false;
    }

    private boolean checkFolderHierarchyAccess(Long folderId, Long userId) {
        Long currentId = folderId;
        while (currentId != null) {
            Directory dir = directoryRepository.findById(currentId).orElse(null);
            if (dir == null) return false;

            // Nếu là chủ hoặc được share folder này -> OK
            if (dir.getUserId().equals(userId)) return true;
            if (shareRepository.existsByReceiverIdAndFolderId(userId, currentId)) return true;

            // Leo lên cha
            currentId = dir.getParentDirectoryId();
        }
        return false;
    }

    // --- API UPLOAD ---
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "directoryId", required = false) Long directoryId) {
        try {
            Long userId = getCurrentUserId();
            fileStorageService.storeFile(file, userId, directoryId);
            return ResponseEntity.ok("Upload thành công: " + file.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Lỗi upload: " + e.getMessage());
        }
    }

    // --- API RECENT FILES ---
    @GetMapping("/recent")
    public ResponseEntity<List<FileInfo>> getRecentFiles() {
        Long userId = getCurrentUserId();
        Pageable topTen = PageRequest.of(0, 100, Sort.by("uploadDate").descending());
        List<FileInfo> recentFiles = fileInfoRepository.findRecentFiles(userId, topTen);
        return ResponseEntity.ok(recentFiles);
    }

    // --- API DOWNLOAD ---
    // Trong FileController.java

    // --- API DOWNLOAD (ĐÃ SỬA LỖI 404 & IN LOG) ---
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        try {
            // 1. Tìm FileInfo trong DB
            FileInfo fileInfo = fileInfoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("File not found in DB with ID: " + id));

            Long userId = getCurrentUserId();

            // 2. Check quyền
            if (!canAccessFile(fileInfo, userId)) {
                System.out.println("User " + userId + " không có quyền truy cập file " + id);
                return ResponseEntity.status(403).build();
            }

            // 3. Load file từ ổ cứng
            Resource resource = fileStorageService.loadFileAsResource(fileInfo.getStoredFilename());

            if (!resource.exists() || !resource.isReadable()) {
                System.err.println("❌ LỖI: File có trong DB nhưng không thấy trong thư mục uploads!");
                System.err.println("   DB Stored Filename: " + fileInfo.getStoredFilename());
                throw new RuntimeException("File physical not found");
            }

            // 4. Xử lý Content-Type an toàn (Tránh lỗi null)
            String contentType = fileInfo.getType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream"; // Mặc định nếu null
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileInfo.getOriginalFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            // [QUAN TRỌNG] In lỗi ra Console của Server để biết tại sao 404
            System.err.println("❌ Lỗi Download File ID " + id + ":");
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    // --- API DELETE ---
    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable Long fileId) {
        Long userId = getCurrentUserId();
        FileInfo file = fileInfoRepository.findById(fileId).orElseThrow();

        // Chỉ chủ sở hữu mới được xóa (Người được share không được xóa file gốc)
        if (!file.getUserId().equals(userId)) return ResponseEntity.status(403).body("Không có quyền xóa");

        file.setInTrash(true);
        fileInfoRepository.save(file);

        if (!trashRepository.existsByUserIdAndFileId(userId, fileId)) {
            Trash trash = new Trash(userId, file, null);
            trashRepository.save(trash);
        }
        return ResponseEntity.ok("Deleted");
    }

    // --- API DASHBOARD METRICS ---
    @GetMapping("/dashboard/metrics")
    public ResponseEntity<?> getDashboardMetrics() {
        Long userId = getCurrentUserId();
        Long activeSize = fileInfoRepository.sumActiveSizeByUserId(userId);
        Long trashSize = fileInfoRepository.sumTrashSizeByUserId(userId);
        return ResponseEntity.ok(new DashboardMetricsDto(
                activeSize == null ? 0 : activeSize,
                trashSize == null ? 0 : trashSize,
                0L
        ));
    }

    // --- API TOGGLE STAR ---
    @PutMapping("/toggle-star")
    public ResponseEntity<?> toggleStar(@RequestParam Long id,
                                        @RequestParam(defaultValue = "false") boolean isFolder,
                                        @RequestParam boolean starred) {
        try {
            Long userId = getCurrentUserId(); // Chuẩn hóa dùng getCurrentUserId

            if (!isFolder) {
                FileInfo file = fileInfoRepository.findById(id).orElse(null);
                if (file == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File không tồn tại");

                // Chỉ chủ sở hữu mới được gắn sao (hoặc tùy logic bạn muốn)
                if (!file.getUserId().equals(userId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Không có quyền");

                file.setStarred(starred);
                fileInfoRepository.save(file);
                return ResponseEntity.ok("Cập nhật thành công");
            } else {
                return ResponseEntity.badRequest().body("Chưa hỗ trợ folder");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi: " + e.getMessage());
        }
    }

    // --- API GET STARRED FILES ---
    @GetMapping("/starred")
    public ResponseEntity<List<FileInfo>> getStarredFiles() {
        Long userId = getCurrentUserId(); // Chuẩn hóa
        List<FileInfo> files = fileInfoRepository.findByUserIdAndIsStarredTrueAndInTrashFalse(userId);
        return ResponseEntity.ok(files);
    }

    // --- API SEARCH (FILE + FOLDER) ---
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchEverything(@RequestParam("query") String query) {
        Long userId = getCurrentUserId(); // Chuẩn hóa

        // 1. Tìm File
        List<FileInfo> files = fileInfoRepository.searchFilesByUserIdAndName(userId, query);

        // 2. Tìm Folder
        List<Directory> dirs = directoryRepository.searchDirectories(userId, query);

        // 3. Kết quả
        Map<String, Object> response = new HashMap<>();
        response.put("files", files);
        response.put("directories", dirs);

        return ResponseEntity.ok(response);
    }
}