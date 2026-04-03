package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.Sample;

import java.util.List;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    List<Sample> findAllByOrderByReceiptDateDesc();

    List<Sample> findAllByForensicCaseIdOrderBySampleNumberAsc(Long caseId);

    boolean existsByForensicCaseIdAndSampleNumber(Long caseId, String sampleNumber);

    boolean existsByForensicCaseIdAndSampleNumberAndIdNot(Long caseId, String sampleNumber, Long id);

    long countByForensicCaseId(Long caseId);
}
