package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.HistologistConclusion;

import java.util.List;
import java.util.Optional;

public interface HistologistConclusionRepository extends JpaRepository<HistologistConclusion, Long> {

    List<HistologistConclusion> findAllByOrderByConclusionDateDesc();

    List<HistologistConclusion> findAllByHistologistIdOrderByConclusionDateDesc(Long histologistId);

    Optional<HistologistConclusion> findBySampleId(Long sampleId);

    boolean existsBySampleId(Long sampleId);

    long countBySampleId(Long sampleId);

    List<HistologistConclusion> findAllByConclusionDateBetweenOrderByConclusionDateDesc(java.time.LocalDate from, java.time.LocalDate to);
}
