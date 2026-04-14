package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.ImageProcessingLog;

import java.time.LocalDate;
import java.util.List;

public interface ImageProcessingLogRepository extends JpaRepository<ImageProcessingLog, Long> {

    List<ImageProcessingLog> findAllByProcessedDateBetween(LocalDate from, LocalDate to);

    void deleteAllByOriginalImageId(Long originalImageId);

    void deleteAllByEnhancedImageId(Long enhancedImageId);
}
