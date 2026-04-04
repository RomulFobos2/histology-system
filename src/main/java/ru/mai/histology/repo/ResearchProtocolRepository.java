package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
