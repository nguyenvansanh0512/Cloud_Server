package com.example.CloudServer.controller;

import com.example.CloudServer.model.User;
import com.example.CloudServer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate; // Import thêm để dùng LocalDate
import java.util.Optional;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired
    private UserRepository userRepository;

    // API lấy thông tin profile
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        // Lấy email từ SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = authentication.getName();

        Optional<User> userOpt = userRepository.findByEmail(currentEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Trả về DTO với đầy đủ thông tin mới
            return ResponseEntity.ok(new UserProfileResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getLastname(),
                    user.getDateOfBirth(), // Thêm ngày sinh
                    user.getNationality()  // Thêm quốc tịch
            ));
        }
        return ResponseEntity.badRequest().body("User not found");
    }

    // Class DTO nội bộ để trả về dữ liệu
    static class UserProfileResponse {
        public Long id;
        public String email;
        public String username;
        public String lastname;
        public LocalDate dateOfBirth; // Thêm trường này
        public String nationality;    // Thêm trường này

        public UserProfileResponse(Long id, String email, String username, String lastname, LocalDate dateOfBirth, String nationality) {
            this.id = id;
            this.email = email;
            this.username = username;
            this.lastname = lastname;
            this.dateOfBirth = dateOfBirth;
            this.nationality = nationality;
        }
    }
}