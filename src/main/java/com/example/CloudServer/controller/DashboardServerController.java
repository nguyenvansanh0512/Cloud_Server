package com.example.CloudServer.controller;

import com.example.CloudServer.model.User;
import com.example.CloudServer.repository.FileInfoRepository;
import com.example.CloudServer.repository.UserRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardServerController {

    @Autowired FileInfoRepository fileInfoRepository;
    @Autowired UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    // DTO trả về số liệu
    @Data
    static class DashboardMetrics {
        private Long activeSize;   // Dung lượng file (Xanh)
        private Long trashSize;    // Dung lượng rác (Đỏ)
        private Long transferToday; // Upload hôm nay
        private Long maxStorage;   // Tổng dung lượng cho phép (Ví dụ 10GB)

        public DashboardMetrics(Long active, Long trash, Long transfer) {
            this.activeSize = active == null ? 0 : active;
            this.trashSize = trash == null ? 0 : trash;
            this.transferToday = transfer == null ? 0 : transfer;
            this.maxStorage = 10L * 1024 * 1024 * 1024; // Mặc định 10GB
        }
    }

    @GetMapping("/metrics")
    public DashboardMetrics getMetrics() {
        User user = getCurrentUser();

        Long active = fileInfoRepository.sumActiveSizeByUserId(user.getId());
        Long trash = fileInfoRepository.sumTrashSizeByUserId(user.getId());

        // Tính từ 00:00 sáng nay
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Long transfer = fileInfoRepository.sumTransferToday(user.getId(), startOfDay);

        return new DashboardMetrics(active, trash, transfer);
    }
}