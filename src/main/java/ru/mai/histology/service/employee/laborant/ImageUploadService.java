package ru.mai.histology.service.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.mapper.MicroscopeImageMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.MicroscopeImage;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.ImageProcessingLogRepository;
import ru.mai.histology.repo.MicroscopeImageRepository;
import ru.mai.histology.repo.SampleRepository;
import ru.mai.histology.service.general.FileStorageService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class ImageUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "tif", "tiff");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    private final MicroscopeImageRepository imageRepository;
    private final ImageProcessingLogRepository processingLogRepository;
    private final SampleRepository sampleRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;

    public ImageUploadService(MicroscopeImageRepository imageRepository,
                              ImageProcessingLogRepository processingLogRepository,
                              SampleRepository sampleRepository,
                              EmployeeRepository employeeRepository,
                              FileStorageService fileStorageService) {
        this.imageRepository = imageRepository;
        this.processingLogRepository = processingLogRepository;
        this.sampleRepository = sampleRepository;
        this.employeeRepository = employeeRepository;
        this.fileStorageService = fileStorageService;
    }

    // ========== Чтение ==========

    @Transactional(readOnly = true)
    public List<MicroscopeImageDTO> getImagesBySample(Long sampleId) {
        List<MicroscopeImage> images = imageRepository.findAllBySampleIdOrderByUploadDateDesc(sampleId);
        return MicroscopeImageMapper.INSTANCE.toDTOList(images);
    }

    @Transactional(readOnly = true)
    public Optional<MicroscopeImageDTO> getImageById(Long id) {
        return imageRepository.findById(id)
                .map(MicroscopeImageMapper.INSTANCE::toDTO);
    }

    // ========== Загрузка ==========

    @Transactional
    public Optional<Long> saveImage(Long sampleId, MultipartFile file,
                                    String description, String magnification) {
        log.info("Загрузка изображения для образца: sampleId={}", sampleId);

        Optional<Sample> sampleOpt = sampleRepository.findById(sampleId);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", sampleId);
            return Optional.empty();
        }

        // Валидация файла
        if (file.isEmpty()) {
            log.error("Пустой файл");
            return Optional.empty();
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            log.error("Недопустимый формат файла: {}", ext);
            return Optional.empty();
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            log.error("Файл превышает допустимый размер: {} байт", file.getSize());
            return Optional.empty();
        }

        try {
            Sample sample = sampleOpt.get();
            String caseNumber = sample.getForensicCase().getCaseNumber();
            String sampleNumber = sample.getSampleNumber();

            // Сохранение файла на диск
            String relativePath = fileStorageService.saveFile(file, caseNumber, sampleNumber);
            if (relativePath == null) {
                log.error("Не удалось сохранить файл на диск");
                return Optional.empty();
            }

            // Генерация миниатюры
            fileStorageService.generateThumbnail(relativePath);

            // Текущий пользователь
            Employee currentUser = employeeRepository.findByUsername(
                    SecurityContextHolder.getContext().getAuthentication().getName()).orElse(null);

            // Сохранение записи в БД
            MicroscopeImage image = new MicroscopeImage();
            image.setOriginalFilename(originalFilename);
            image.setStoredFilename(relativePath.substring(relativePath.lastIndexOf("/") + 1));
            image.setFilePath(relativePath);
            image.setFileSize(file.getSize());
            image.setContentType(file.getContentType());
            image.setUploadDate(LocalDate.now());
            image.setDescription(description);
            image.setMagnification(magnification);
            image.setEnhanced(false);
            image.setSample(sample);
            image.setUploadedBy(currentUser);

            imageRepository.save(image);
            log.info("Изображение сохранено: id={}, файл={}", image.getId(), relativePath);
            return Optional.of(image.getId());
        } catch (Exception e) {
            log.error("Ошибка при загрузке изображения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Удаление ==========

    @Transactional
    public boolean deleteImage(Long id) {
        log.info("Удаление изображения: id={}", id);

        Optional<MicroscopeImage> imageOpt = imageRepository.findById(id);
        if (imageOpt.isEmpty()) {
            log.error("Изображение не найдено: id={}", id);
            return false;
        }

        try {
            MicroscopeImage image = imageOpt.get();

            // Удаление улучшенных копий (если это оригинал)
            List<MicroscopeImage> enhancedCopies = imageRepository.findAllByOriginalImageId(id);
            for (MicroscopeImage enhanced : enhancedCopies) {
                processingLogRepository.deleteAllByEnhancedImageId(enhanced.getId());
                processingLogRepository.deleteAllByOriginalImageId(enhanced.getId());
                deleteFileAndThumb(enhanced.getFilePath());
                imageRepository.delete(enhanced);
            }

            // Удаление логов обработки, ссылающихся на это изображение
            processingLogRepository.deleteAllByOriginalImageId(id);
            processingLogRepository.deleteAllByEnhancedImageId(id);

            // Удаление файлов с диска (оригинал + миниатюра)
            deleteFileAndThumb(image.getFilePath());

            imageRepository.deleteById(id);
            log.info("Изображение удалено: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении изображения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    /**
     * Удаление всех изображений образца (каскад при удалении образца).
     */
    @Transactional
    public void deleteAllImagesBySample(Long sampleId) {
        log.info("Каскадное удаление изображений образца: sampleId={}", sampleId);
        List<MicroscopeImage> images = imageRepository.findAllBySampleId(sampleId);
        for (MicroscopeImage image : images) {
            processingLogRepository.deleteAllByOriginalImageId(image.getId());
            processingLogRepository.deleteAllByEnhancedImageId(image.getId());
            deleteFileAndThumb(image.getFilePath());
        }
        imageRepository.deleteAll(images);
    }

    // ========== Утилиты ==========

    private void deleteFileAndThumb(String relativePath) {
        fileStorageService.deleteFile(relativePath);
        String thumbPath = getThumbPath(relativePath);
        if (thumbPath != null) {
            fileStorageService.deleteFile(thumbPath);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private String getThumbPath(String relativePath) {
        if (relativePath == null) return null;
        int lastSlash = relativePath.lastIndexOf("/");
        if (lastSlash < 0) return "thumb_" + relativePath;
        return relativePath.substring(0, lastSlash + 1) + "thumb_" + relativePath.substring(lastSlash + 1);
    }
}
