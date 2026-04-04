package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.ImageProcessingLog;

public interface ImageProcessingLogRepository extends JpaRepository<ImageProcessingLog, Long> {

    void deleteAllByOriginalImageId(Long originalImageId);

    void deleteAllByEnhancedImageId(Long enhancedImageId);
}
