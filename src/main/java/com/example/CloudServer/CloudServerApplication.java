package com.example.CloudServer;

import com.example.CloudServer.config.FileStorageProperties;
import com.example.CloudServer.model.User;
import com.example.CloudServer.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableConfigurationProperties({
        FileStorageProperties.class
})
public class CloudServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudServerApplication.class, args);
        System.out.println("Cloud Storage Server đã khởi động thành công trên http://localhost:8080");
        System.out.println("Sử dụng tài khoản mặc định: admin/password");
    }


}