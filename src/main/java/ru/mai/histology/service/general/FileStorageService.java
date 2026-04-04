package ru.mai.histology.service.general;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private Path basePath;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            // Преобразуем в абсолютный путь, чтобы не зависеть от working directory Tomcat
            basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }
            log.info("Директория для загрузок: {}", basePath);
        } catch (IOException e) {
            log.error("Не удалось создать директорию загрузок: {}", e.getMessage(), e);
            basePath = Paths.get(uploadDir).toAbsolutePath();
        }
    }

    /**
     * Сохраняет файл на диск.
     * @return относительный путь от uploadDir (например "CASE-001/S-001/uuid.jpg")
     */
    public String saveFile(MultipartFile file, String caseNumber, String sampleNumber) {
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String storedFilename = UUID.randomUUID() + ext;

            Path dir = basePath.resolve(caseNumber).resolve(sampleNumber);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            Path filePath = dir.resolve(storedFilename);
            file.transferTo(filePath.toFile());

            String relativePath = caseNumber + "/" + sampleNumber + "/" + storedFilename;
            log.info("Файл сохранён: {} (абсолютный: {})", relativePath, filePath);
            return relativePath;
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Читает файл с диска.
     */
    public byte[] readFile(String relativePath) {
        try {
            Path path = basePath.resolve(relativePath);
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
            log.warn("Файл не найден: {}", path);
            return null;
        } catch (IOException e) {
            log.error("Ошибка при чтении файла: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Удаляет файл с диска.
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path path = basePath.resolve(relativePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Файл удалён: {}", relativePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Генерирует миниатюру (300px по большей стороне).
     * @return относительный путь миниатюры или null при ошибке
     */
    public String generateThumbnail(String relativePath) {
        try {
            Path originalPath = basePath.resolve(relativePath);
            BufferedImage original = ImageIO.read(originalPath.toFile());
            if (original == null) {
                log.warn("Не удалось прочитать изображение для миниатюры: {}", relativePath);
                return null;
            }

            int maxSize = 300;
            int w = original.getWidth();
            int h = original.getHeight();
            double scale = Math.min((double) maxSize / w, (double) maxSize / h);
            if (scale >= 1.0) scale = 1.0;

            int newW = (int) (w * scale);
            int newH = (int) (h * scale);

            BufferedImage thumb = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, newW, newH, null);
            g.dispose();

            String thumbFilename = "thumb_" + Paths.get(relativePath).getFileName();
            Path thumbPath = originalPath.getParent().resolve(thumbFilename);
            ImageIO.write(thumb, "jpg", thumbPath.toFile());

            String thumbRelative = relativePath.substring(0, relativePath.lastIndexOf("/") + 1) + thumbFilename;
            log.info("Миниатюра создана: {}", thumbRelative);
            return thumbRelative;
        } catch (Exception e) {
            log.error("Ошибка при генерации миниатюры: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Удаляет все файлы образца (директорию).
     */
    public void deleteDirectory(String caseNumber, String sampleNumber) {
        try {
            Path dir = basePath.resolve(caseNumber).resolve(sampleNumber);
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Не удалось удалить: {}", path, e);
                            }
                        });
                log.info("Директория удалена: {}/{}", caseNumber, sampleNumber);
            }
        } catch (IOException e) {
            log.error("Ошибка при удалении директории: {}", e.getMessage(), e);
        }
    }

    /**
     * Определяет content type по расширению файла.
     */
    public String getContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        if (lower.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
