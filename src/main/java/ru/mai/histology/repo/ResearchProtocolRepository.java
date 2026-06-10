package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.histology.models.ResearchProtocol;

import java.util.List;
import java.util.Optional;

public interface ResearchProtocolRepository extends JpaRepository<ResearchProtocol, Long> {

    List<ResearchProtocol> findAllByOrderByCreatedDateDesc();

    List<ResearchProtocol> findAllByCreatedByIdOrderByCreatedDateDesc(Long createdById);

    Optional<ResearchProtocol> findBySampleId(Long sampleId);

    boolean existsBySampleId(Long sampleId);

    boolean existsByProtocolNumber(String protocolNumber);

    boolean existsByProtocolNumberAndIdNot(String protocolNumber, Long id);

    long countBySampleId(Long sampleId);

    /**
     * Возвращает максимальный порядковый номер N среди всех протоколов
     * с номером вида {prefix}/{N} (например, при prefix="П-25-05-26"). Если
     * таких протоколов нет — возвращает 0. Используется для автогенерации
     * следующего protocolNumber за текущий день. MAX (а не COUNT) даёт
     * монотонно растущий номер с учётом удалённых записей.
     *
     * Имена таблицы и колонки — snake_case, потому что Spring Boot Hibernate
     * по умолчанию применяет SpringPhysicalNamingStrategy и конвертирует
     * @Table(name="t_researchProtocol") → реальное t_research_protocol,
     * поле protocolNumber → колонка protocol_number.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(protocol_number, '/', -1) AS UNSIGNED)), 0) " +
                   "FROM t_research_protocol WHERE protocol_number LIKE CONCAT(:prefix, '/%')",
           nativeQuery = true)
    Integer findMaxSequenceForPrefix(@Param("prefix") String prefix);
}
