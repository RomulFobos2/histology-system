package ru.mai.histology.service.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.mapper.MicroscopeImageMapper;
import ru.mai.histology.models.MicroscopeImage;
import ru.mai.histology.repo.MicroscopeImageRepository;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ImageViewService {

    private final MicroscopeImageRepository imageRepository;

    public ImageViewService(MicroscopeImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

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

    @Transactional(readOnly = true)
    public Optional<MicroscopeImageDTO> getLatestEnhancedVersion(Long originalImageId) {
        return imageRepository.findFirstByOriginalImageIdOrderByUploadDateDesc(originalImageId)
                .map(MicroscopeImageMapper.INSTANCE::toDTO);
    }
}
