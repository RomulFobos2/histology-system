package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.MicroscopeImage;

import java.util.List;

public interface MicroscopeImageRepository extends JpaRepository<MicroscopeImage, Long> {

    List<MicroscopeImage> findAllBySampleIdOrderByUploadDateDesc(Long sampleId);

    long countBySampleId(Long sampleId);

    List<MicroscopeImage> findAllBySampleId(Long sampleId);
}
