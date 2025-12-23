package com.example.CloudServer.service;

import com.example.CloudServer.config.FileStorageProperties;
import com.example.CloudServer.model.FileInfo;
import com.example.CloudServer.repository.FileInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    @Autowired
    private FileInfoRepository fileInfoRepository;

    @Autowired
    public FileStorageService(FileStorageProperties fileStorageProperties) {
        // Lấy đường dẫn từ file properties (mặc định là "uploads")
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ file.", ex);
        }
    }

    // Hàm lưu file
    public FileInfo storeFile(MultipartFile file, Long userId, Long directoryId) {
        // Lấy tên file gốc
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            if(originalFileName.contains("..")) {
                throw new RuntimeException("Tên file chứa ký tự không hợp lệ " + originalFileName);
            }

            // Tạo tên file duy nhất để lưu trên server (tránh trùng tên)
            String storedFileName = UUID.randomUUID().toString() + "_" + originalFileName;

            // Copy file vào thư mục đích
            Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Lưu Metadata vào DB
            FileInfo fileInfo = new FileInfo();
            fileInfo.setOriginalFilename(originalFileName);
            fileInfo.setStoredFilename(storedFileName);
            fileInfo.setSize(file.getSize());
            fileInfo.setUserId(userId);
            fileInfo.setDirectoryId(directoryId);

            return fileInfoRepository.save(fileInfo);

        } catch (IOException ex) {
            throw new RuntimeException("Không thể lưu file " + originalFileName + ". Vui lòng thử lại!", ex);
        }
    }

    // Hàm đọc file (cho chức năng Download)
    public Resource loadFileAsResource(String storedFilename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storedFilename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if(resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File không tìm thấy: " + storedFilename);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File không tìm thấy " + storedFilename, ex);
        }
    }

    public void deleteFilePhysical(String storedFilename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storedFilename).normalize();
            Files.deleteIfExists(filePath); // Xóa file nếu tồn tại
        } catch (IOException e) {
            System.err.println("Không thể xóa file vật lý: " + storedFilename);
            e.printStackTrace();
        }
    }
}