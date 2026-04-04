package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.models.Sample;

import java.time.LocalDate;
import java.util.List;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    List<Sample> findAllByOrderByReceiptDateDesc();

    List<Sample> findAllByForensicCaseIdOrderBySampleNumberAsc(Long caseId);

    boolean existsByForensicCaseIdAndSampleNumber(Long caseId, String sampleNumber);

    boolean existsByForensicCaseIdAndSampleNumberAndIdNot(Long caseId, String sampleNumber, Long id);

    long countByForensicCaseId(Long caseId);

    // --- Stage 7+8: Reports & Journal ---

    List<Sample> findAllByReceiptDateBetweenOrderByReceiptDateDesc(LocalDate from, LocalDate to);

    long countByStatus(SampleStatus status);

    List<Sample> findAllByReceiptDateGreaterThanEqual(LocalDate fromDate);

    @Query("SELECT s FROM Sample s JOIN FETCH s.forensicCase LEFT JOIN FETCH s.assignedHistologist LEFT JOIN FETCH s.registeredBy ORDER BY s.receiptDate DESC")
    List<Sample> findAllWithCaseAndEmployees();
}
