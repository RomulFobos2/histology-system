package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.histology.models.Sample;

import java.time.LocalDate;
import java.util.List;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    List<Sample> findAllByOrderByReceiptDateDesc();

    List<Sample> findAllByAssignedHistologistIdOrderByReceiptDateDesc(Long histologistId);

    List<Sample> findAllByReceiptDateBetween(LocalDate from, LocalDate to);

    List<Sample> findAllByForensicCaseIdOrderBySampleNumberAsc(Long caseId);

    boolean existsByForensicCaseIdAndSampleNumber(Long caseId, String sampleNumber);

    boolean existsByForensicCaseIdAndSampleNumberAndIdNot(Long caseId, String sampleNumber, Long id);

    long countByForensicCaseId(Long caseId);

    @Query(value = "SELECT COALESCE(MAX(CAST(sample_number AS UNSIGNED)), 0) " +
                   "FROM t_sample WHERE forensic_case_id = :caseId " +
                   "AND sample_number REGEXP '^[0-9]+$'",
           nativeQuery = true)
    Integer findMaxSampleNumberForCase(@Param("caseId") Long caseId);
}
