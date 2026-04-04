package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.ForensicConclusion;

import java.util.List;
import java.util.Optional;

public interface ForensicConclusionRepository extends JpaRepository<ForensicConclusion, Long> {

    List<ForensicConclusion> findAllByOrderByConclusionDateDesc();

    Optional<ForensicConclusion> findBySampleId(Long sampleId);

    boolean existsBySampleId(Long sampleId);

    long countBySampleId(Long sampleId);

    List<ForensicConclusion> findAllByConclusionDateBetweenOrderByConclusionDateDesc(java.time.LocalDate from, java.time.LocalDate to);
}
