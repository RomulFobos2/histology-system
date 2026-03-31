package ru.mai.histology.service.general;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads/materials}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("Директория для загрузок: {}", uploadPath);
        } catch (IOException e) {
            log.error("Не удалось создать директорию для загрузок: {}", e.getMessage(), e);
        }
    }

    public String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.error("Файл пуст или не передан");
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            log.error("Недопустимый тип файла: {}", originalFilename);
            return null;
        }

        try {
            String filename = UUID.randomUUID() + ".pdf";
            Path targetPath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Файл сохранён: {}", filename);
            return filename;
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean deleteFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        try {
            Path filePath = uploadPath.resolve(filename).normalize();
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Файл удалён: {}", filename);
                return true;
            }
            log.warn("Файл не найден для удаления: {}", filename);
            return false;
        } catch (IOException e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            return false;
        }
    }

    public Path getFilePath(String filename) {
        return uploadPath.resolve(filename).normalize();
    }
}