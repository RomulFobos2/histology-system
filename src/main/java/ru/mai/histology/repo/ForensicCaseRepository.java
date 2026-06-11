package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.histology.models.ForensicCase;

import java.util.List;

public interface ForensicCaseRepository extends JpaRepository<ForensicCase, Long> {

    List<ForensicCase> findAllByOrderByReceiptDateDesc();

    boolean existsByCaseNumber(String caseNumber);

    boolean existsByCaseNumberAndIdNot(String caseNumber, Long id);

    /**
     * Возвращает максимальный порядковый номер N среди всех дел с номером
     * вида {prefix}/{N} (например, при prefix="Д-25-05-26" вернёт MAX
     * последней цифровой части). Если таких дел нет — возвращает 0.
     * Используется для автогенерации следующего caseNumber за текущий день
     * с учётом удалённых записей (MAX гарантирует монотонный рост).
     *
     * Имена таблицы и колонки — snake_case, потому что Spring Boot Hibernate
     * по умолчанию применяет SpringPhysicalNamingStrategy и конвертирует
     * @Table(name="t_forensicCase") → реальное t_forensic_case,
     * поле caseNumber → колонка case_number.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(case_number, '/', -1) AS UNSIGNED)), 0) " +
                   "FROM t_forensic_case WHERE case_number LIKE CONCAT(:prefix, '/%')",
           nativeQuery = true)
    Integer findMaxSequenceForPrefix(@Param("prefix") String prefix);
}
