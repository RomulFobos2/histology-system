package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.ForensicConclusion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ForensicConclusionRepository extends JpaRepository<ForensicConclusion, Long> {

    List<ForensicConclusion> findAllByOrderByConclusionDateDesc();

    List<ForensicConclusion> findAllByConclusionDateBetween(LocalDate from, LocalDate to);

    Optional<ForensicConclusion> findBySampleId(Long sampleId);

    boolean existsBySampleId(Long sampleId);

    long countBySampleId(Long sampleId);
}
