package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.MicroscopeImage;

import java.util.List;
import java.util.Optional;

public interface MicroscopeImageRepository extends JpaRepository<MicroscopeImage, Long> {

    List<MicroscopeImage> findAllByOrderByUploadDateDesc();

    List<MicroscopeImage> findAllBySampleIdOrderByUploadDateDesc(Long sampleId);

    long countBySampleId(Long sampleId);

    List<MicroscopeImage> findAllBySampleId(Long sampleId);

    Optional<MicroscopeImage> findFirstByOriginalImageIdOrderByUploadDateDesc(Long originalImageId);

    List<MicroscopeImage> findAllByOriginalImageId(Long originalImageId);
}
