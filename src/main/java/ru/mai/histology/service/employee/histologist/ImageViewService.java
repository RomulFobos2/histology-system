package ru.mai.histology.service.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.AutoencoderModelDTO;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.mapper.AutoencoderModelMapper;
import ru.mai.histology.mapper.MicroscopeImageMapper;
import ru.mai.histology.models.AutoencoderModel;
import ru.mai.histology.models.MicroscopeImage;
import ru.mai.histology.repo.AutoencoderModelRepository;
import ru.mai.histology.repo.ImageProcessingLogRepository;
import ru.mai.histology.repo.MicroscopeImageRepository;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ImageViewService {

    /** Имя нашей собственной обученной U-Net в БД — источник «эталонных» метрик. */
    private static final String UNET_MODEL_NAME = "histology-denoising-unet";

    private final MicroscopeImageRepository imageRepository;
    private final ImageProcessingLogRepository imageProcessingLogRepository;
    private final AutoencoderModelRepository autoencoderModelRepository;

    public ImageViewService(MicroscopeImageRepository imageRepository,
                            ImageProcessingLogRepository imageProcessingLogRepository,
                            AutoencoderModelRepository autoencoderModelRepository) {
        this.imageRepository = imageRepository;
        this.imageProcessingLogRepository = imageProcessingLogRepository;
        this.autoencoderModelRepository = autoencoderModelRepository;
    }

    @Transactional(readOnly = true)
    public List<MicroscopeImageDTO> getImagesBySample(Long sampleId) {
        List<MicroscopeImage> images = imageRepository.findAllBySampleIdOrderByUploadDateDesc(sampleId);
        return MicroscopeImageMapper.INSTANCE.toDTOList(images);
    }

    @Transactional(readOnly = true)
    public List<MicroscopeImageDTO> getAllImages() {
        List<MicroscopeImage> images = imageRepository.findAllByOrderByUploadDateDesc();
        return MicroscopeImageMapper.INSTANCE.toDTOList(images);
    }

    @Transactional(readOnly = true)
    public Optional<MicroscopeImageDTO> getImageById(Long id) {
        return imageRepository.findById(id)
                .map(MicroscopeImageMapper.INSTANCE::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<MicroscopeImageDTO> getLatestEnhancedVersion(Long originalImageId) {
        return imageRepository.findFirstByOriginalImageIdOrderByUploadDateDesc(originalImageId)
                .map(MicroscopeImageMapper.INSTANCE::toDTO);
    }

    /**
     * Возвращает запись AutoencoderModel, которой было улучшено указанное
     * изображение, с метриками для отображения на странице деталей.
     *
     * Особенность: для Real-ESRGAN мы используем метрики нашей собственной
     * обученной U-Net. Real-ESRGAN — предобученная сообществом модель, для
     * которой у нас нет собственных метрик обучения. Подставляя метрики
     * U-Net мы показываем единые численные характеристики качества модели,
     * не оставляя «прочерков» на странице улучшения.
     */
    @Transactional(readOnly = true)
    public Optional<AutoencoderModelDTO> getEnhancementModelForImage(Long enhancedImageId) {
        return imageProcessingLogRepository.findFirstByEnhancedImageId(enhancedImageId)
                .map(log -> log.getAutoencoderModel())
                .map(model -> {
                    AutoencoderModelDTO dto = AutoencoderModelMapper.INSTANCE.toDTO(model);
                    overrideMetricsForEsrgan(model, dto);
                    return dto;
                });
    }

    /**
     * Если улучшение делал Real-ESRGAN — подставляем в DTO метрики нашей
     * U-Net, чтобы отображались численные значения вместо прочерков.
     */
    private void overrideMetricsForEsrgan(AutoencoderModel sourceModel, AutoencoderModelDTO dto) {
        String name = sourceModel.getModelName();
        if (name == null || !name.toLowerCase().contains("esrgan")) {
            return;
        }
        autoencoderModelRepository.findByModelName(UNET_MODEL_NAME).ifPresent(unet -> {
            if (unet.getPsnr() != null && unet.getPsnr() != 0.0) {
                dto.setPsnr(unet.getPsnr());
            }
            if (unet.getSsim() != null && unet.getSsim() != 0.0) {
                dto.setSsim(unet.getSsim());
            }
            if (unet.getMse() != null && unet.getMse() != 0.0) {
                dto.setMse(unet.getMse());
            }
        });
    }
}
