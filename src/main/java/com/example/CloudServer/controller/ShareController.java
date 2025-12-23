package com.example.CloudServer.controller;

import com.example.CloudServer.model.*;
import com.example.CloudServer.repository.*;
import com.fasterxml.jackson.annotation.JsonProperty; // Import cái này
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    @Autowired ShareRepository shareRepository;
    @Autowired UserRepository userRepository;
    @Autowired FileInfoRepository fileInfoRepository;
    @Autowired DirectoryRepository directoryRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    // DTO nhận dữ liệu từ Client
    @Data
    static class ShareRequest {
        private String email;
        private Long itemId;

        // Mẹo: Thêm @JsonProperty để đảm bảo map đúng với JSON "isFolder": true từ Client
        @JsonProperty("isFolder")
        private boolean isFolder;
    }

    // DTO trả về cho Client
    @Data
    static class ShareResponse {
        private Long shareId;
        private String itemName;
        private String itemType; // "FILE" hoặc "FOLDER"
        private Long itemId;
        private String senderEmail;
        private String sharedDate;

        public ShareResponse(Long shareId, String itemName, String itemType, Long itemId, String senderEmail, String sharedDate) {
            this.shareId = shareId;
            this.itemName = itemName;
            this.itemType = itemType;
            this.itemId = itemId;
            this.senderEmail = senderEmail;
            this.sharedDate = sharedDate;
        }
    }

    // --- API CHIA SẺ ---
    @PostMapping("/share")
    public ResponseEntity<?> shareItem(@RequestBody ShareRequest request) {
        try {
            User sender = getCurrentUser();
            User receiver = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng có email: " + request.getEmail()));

            if (sender.getId().equals(receiver.getId())) {
                return ResponseEntity.badRequest().body("Không thể chia sẻ cho chính mình.");
            }

            Share share = new Share();
            share.setSender(sender);
            share.setReceiver(receiver);
            share.setPermission("VIEW");

            // --- PHÂN BIỆT LOGIC FILE VÀ FOLDER ---
            if (request.isFolder()) {
                // Nếu là Folder: Tìm trong bảng Directory
                Directory dir = directoryRepository.findById(request.getItemId())
                        .orElseThrow(() -> new RuntimeException("Thư mục không tồn tại"));

                // Kiểm tra xem đã share chưa để tránh trùng
                if(shareRepository.existsByReceiverIdAndFolderId(receiver.getId(), dir.getId())) {
                    return ResponseEntity.badRequest().body("Bạn đã chia sẻ thư mục này cho họ rồi.");
                }

                share.setFolder(dir);
                share.setFile(null); // File phải null
            } else {
                // Nếu là File: Tìm trong bảng FileInfo
                FileInfo file = fileInfoRepository.findById(request.getItemId())
                        .orElseThrow(() -> new RuntimeException("File không tồn tại"));

                if(shareRepository.existsByReceiverIdAndFileId(receiver.getId(), file.getId())) {
                    return ResponseEntity.badRequest().body("Bạn đã chia sẻ file này cho họ rồi.");
                }

                share.setFile(file);
                share.setFolder(null); // Folder phải null
            }

            shareRepository.save(share);
            return ResponseEntity.ok("Chia sẻ thành công cho " + request.getEmail());

        } catch (Exception e) {
            e.printStackTrace(); // Xem log lỗi server nếu có
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // --- API LẤY DANH SÁCH ĐƯỢC CHIA SẺ ---
    @GetMapping("/received")
    public ResponseEntity<List<ShareResponse>> getReceivedShares() {
        User user = getCurrentUser();
        List<Share> shares = shareRepository.findByReceiverIdOrderBySharedAtDesc(user.getId());

        List<ShareResponse> response = shares.stream().map(s -> {
            boolean isFolder = (s.getFolder() != null);
            return new ShareResponse(
                    s.getId(),
                    isFolder ? s.getFolder().getName() : s.getFile().getOriginalFilename(),
                    isFolder ? "FOLDER" : "FILE",
                    isFolder ? s.getFolder().getId() : s.getFile().getId(),
                    s.getSender().getEmail(),
                    s.getSharedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}