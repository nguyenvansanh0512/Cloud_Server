package com.example.CloudServer.controller;

import com.example.CloudServer.model.User;
import com.example.CloudServer.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // Key bí mật dài (đã sửa ở bước trước)
    private static final String SECRET_KEY_STRING = "ThisIsAVeryLongSecretKeyThatIsRequiredForHS512AlgorithmToWorkCorrectlyAndSecurelySoPleaseDoNotChangeIt";
    private final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_KEY_STRING.getBytes(StandardCharsets.UTF_8));

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String emailInput = body.get("username");
        String passwordInput = body.get("password");

        // --- LOG LOG LOG ---
        System.out.println("-------------------------------------------");
        System.out.println("🔑 [SERVER] NHẬN YÊU CẦU LOGIN:");
        System.out.println("   - Email: " + emailInput);
        // -------------------

        Optional<User> userOpt = userRepository.findByEmail(emailInput);

        if (userOpt.isEmpty()) {
            System.out.println("❌ Lỗi: Email không tồn tại!");
            return ResponseEntity.status(401).body("Tài khoản không tồn tại.");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(passwordInput, user.getPassword())) {
            System.out.println("❌ Lỗi: Sai mật khẩu!");
            return ResponseEntity.status(401).body("Sai mật khẩu.");
        }

        System.out.println("✅ Đăng nhập THÀNH CÔNG! Token đã được cấp.");

        String token = Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 864000000))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        System.out.println("📝 [SERVER] ĐANG ĐĂNG KÝ USER MỚI: " + body.get("email"));

        String email = body.get("email");
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body("Email đã tồn tại!");
        }
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setUsername(body.get("username"));
        newUser.setPassword(passwordEncoder.encode(body.get("password")));
        userRepository.save(newUser);
        return ResponseEntity.ok("Đăng ký thành công");
    }
}