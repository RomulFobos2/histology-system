package ru.mai.histology.service.employee.histologist;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.histology.models.AutoencoderModel;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ImageProcessingLog;
import ru.mai.histology.models.MicroscopeImage;
import ru.mai.histology.repo.AutoencoderModelRepository;
import ru.mai.histology.repo.ImageProcessingLogRepository;
import ru.mai.histology.repo.MicroscopeImageRepository;
import ru.mai.histology.service.employee.EmployeeService;
import ru.mai.histology.service.general.AutoencoderClientService;
import ru.mai.histology.service.general.FileStorageService;

import java.time.LocalDate;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class ImageEnhancementService {

    private final MicroscopeImageRepository microscopeImageRepository;
    private final AutoencoderModelRepository autoencoderModelRepository;
    private final ImageProcessingLogRepository imageProcessingLogRepository;
    private final FileStorageService fileStorageService;
    private final AutoencoderClientService autoencoderClientService;
    private final EmployeeService employeeService;

    public ImageEnhancementService(MicroscopeImageRepository microscopeImageRepository,
                                   AutoencoderModelRepository autoencoderModelRepository,
                                   ImageProcessingLogRepository imageProcessingLogRepository,
                                   FileStorageService fileStorageService,
                                   AutoencoderClientService autoencoderClientService,
                                   EmployeeService employeeService) {
        this.microscopeImageRepository = microscopeImageRepository;
        this.autoencoderModelRepository = autoencoderModelRepository;
        this.imageProcessingLogRepository = imageProcessingLogRepository;
        this.fileStorageService = fileStorageService;
        this.autoencoderClientService = autoencoderClientService;
        this.employeeService = employeeService;
    }

    @Transactional
    public Optional<Long> enhanceImage(Long imageId) {
        Optional<MicroscopeImage> originalImageOpt = microscopeImageRepository.findById(imageId);
        if (originalImageOpt.isEmpty()) {
            log.error("Изображение не найдено: id={}", imageId);
            return Optional.empty();
        }

        MicroscopeImage originalImage = originalImageOpt.get();
        byte[] originalBytes = fileStorageService.readFile(originalImage.getFilePath());
        if (originalBytes == null || originalBytes.length == 0) {
            log.error("Не удалось прочитать исходный файл изображения: id={}", imageId);
            return Optional.empty();
        }

        long startedAt = System.currentTimeMillis();
        Optional<AutoencoderClientService.EnhancedImageResponse> responseOpt =
                autoencoderClientService.enhanceImage(
                        originalImage.getOriginalFilename(),
                        originalImage.getContentType(),
                        originalBytes
                );
        if (responseOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            AutoencoderClientService.EnhancedImageResponse response = responseOpt.get();
            String relativePath = fileStorageService.saveBytesAsImage(
                    response.imageBytes(),
                    "enhanced_" + originalImage.getOriginalFilename(),
                    originalImage.getSample().getForensicCase().getCaseNumber(),
                    originalImage.getSample().getSampleNumber(),
                    response.contentType()
            );
            if (relativePath == null) {
                log.error("Не удалось сохранить улучшенное изображение на диск: originalId={}", imageId);
                return Optional.empty();
            }

            fileStorageService.generateThumbnail(relativePath);

            Employee currentEmployee = employeeService.getAuthenticationEmployee();

            MicroscopeImage enhancedImage = new MicroscopeImage();
            enhancedImage.setOriginalFilename("enhanced_" + originalImage.getOriginalFilename());
            enhancedImage.setStoredFilename(relativePath.substring(relativePath.lastIndexOf("/") + 1));
            enhancedImage.setFilePath(relativePath);
            enhancedImage.setFileSize((long) response.imageBytes().length);
            enhancedImage.setContentType(response.contentType());
            enhancedImage.setUploadDate(LocalDate.now());
            enhancedImage.setDescription(buildEnhancedDescription(originalImage.getDescription()));
            enhancedImage.setMagnification(originalImage.getMagnification());
            enhancedImage.setEnhanced(true);
            enhancedImage.setSample(originalImage.getSample());
            enhancedImage.setUploadedBy(currentEmployee);
            enhancedImage.setOriginalImage(originalImage);

            microscopeImageRepository.save(enhancedImage);

            AutoencoderModel autoencoderModel = resolveAutoencoderModel(response.modelName());

            ImageProcessingLog processingLog = new ImageProcessingLog();
            processingLog.setProcessedDate(LocalDate.now());
            processingLog.setProcessingTimeMs(System.currentTimeMillis() - startedAt);
            processingLog.setOriginalImage(originalImage);
            processingLog.setEnhancedImage(enhancedImage);
            processingLog.setAutoencoderModel(autoencoderModel);
            processingLog.setProcessedBy(currentEmployee);
            imageProcessingLogRepository.save(processingLog);

            log.info("Улучшенное изображение сохранено: originalId={}, enhancedId={}", imageId, enhancedImage.getId());
            return Optional.of(enhancedImage.getId());
        } catch (Exception e) {
            log.error("Ошибка при улучшении изображения id={}: {}", imageId, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    private AutoencoderModel resolveAutoencoderModel(String modelName) {
        String normalizedModelName = (modelName == null || modelName.isBlank())
                ? "unknown-autoencoder-model"
                : modelName;

        Optional<AutoencoderModel> autoencoderModelOptional = autoencoderModelRepository.findByModelName(normalizedModelName);
        if (autoencoderModelOptional.isPresent()) {
            AutoencoderModel model = autoencoderModelOptional.get();
            model.setActive(true);
            return autoencoderModelRepository.save(model);
        }

        AutoencoderModel autoencoderModel = new AutoencoderModel();
        autoencoderModel.setModelName(normalizedModelName);
        autoencoderModel.setDescription("Модель, использованная Python-сервисом для улучшения изображения");
        autoencoderModel.setTrainedDate(LocalDate.now());
        autoencoderModel.setEpochs(0);
        autoencoderModel.setLoss(0.0);
        autoencoderModel.setValidationLoss(0.0);
        autoencoderModel.setActive(true);
        return autoencoderModelRepository.save(autoencoderModel);
    }

    private String buildEnhancedDescription(String originalDescription) {
        if (originalDescription == null || originalDescription.isBlank()) {
            return "Улучшенная копия, созданная Python-сервисом";
        }
        return originalDescription + " | Улучшенная копия, созданная Python-сервисом";
    }
}
