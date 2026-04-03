package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.ForensicCase;

import java.util.List;

public interface ForensicCaseRepository extends JpaRepository<ForensicCase, Long> {

    List<ForensicCase> findAllByOrderByReceiptDateDesc();

    boolean existsByCaseNumber(String caseNumber);

    boolean existsByCaseNumberAndIdNot(String caseNumber, Long id);
}
