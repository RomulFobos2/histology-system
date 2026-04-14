package ru.mai.histology.controllers.general;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.mai.histology.models.MicroscopeImage;
import ru.mai.histology.repo.MicroscopeImageRepository;
import ru.mai.histology.service.general.FileStorageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Отдача файлов изображений по ID (безопасно — без path traversal).
 * URL /files/** уже в permitAll в SecurityConfig.
 */
@Controller
@Slf4j
public class ImageFileController {

    private final MicroscopeImageRepository imageRepository;
    private final FileStorageService fileStorageService;

    public ImageFileController(MicroscopeImageRepository imageRepository,
                               FileStorageService fileStorageService) {
        this.imageRepository = imageRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Отдача оригинального изображения по ID.
     */
    @GetMapping("/files/images/serve/{id}")
    public ResponseEntity<byte[]> serveImage(@PathVariable Long id) {
        Optional<MicroscopeImage> imageOpt = imageRepository.findById(id);
        if (imageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MicroscopeImage image = imageOpt.get();
        byte[] data = fileStorageService.readFile(image.getFilePath());
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        String contentType = image.getContentType();
        if (contentType == null) {
            contentType = fileStorageService.getContentType(image.getOriginalFilename());
        }

        // Конвертация TIFF → PNG для отображения в браузере
        if ("image/tiff".equalsIgnoreCase(contentType)) {
            try {
                BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(data));
                if (buffered != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(buffered, "png", baos);
                    data = baos.toByteArray();
                    contentType = "image/png";
                }
            } catch (Exception e) {
                log.warn("Не удалось сконвертировать TIFF в PNG для id={}: {}", id, e.getMessage());
            }
        }

        // RFC 5987: безопасный Content-Disposition для Unicode-имён
        String originalFilename = image.getOriginalFilename();
        String asciiName = originalFilename.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * Отдача миниатюры по ID изображения.
     */
    @GetMapping("/files/images/thumb/{id}")
    public ResponseEntity<byte[]> serveThumb(@PathVariable Long id) {
        Optional<MicroscopeImage> imageOpt = imageRepository.findById(id);
        if (imageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MicroscopeImage image = imageOpt.get();
        String relativePath = image.getFilePath();

        // Путь к миниатюре
        String thumbPath;
        int lastSlash = relativePath.lastIndexOf("/");
        if (lastSlash < 0) {
            thumbPath = "thumb_" + relativePath;
        } else {
            thumbPath = relativePath.substring(0, lastSlash + 1) + "thumb_" + relativePath.substring(lastSlash + 1);
        }

        byte[] data = fileStorageService.readFile(thumbPath);
        if (data == null) {
            // Fallback — отдаём оригинал
            data = fileStorageService.readFile(relativePath);
        }
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
