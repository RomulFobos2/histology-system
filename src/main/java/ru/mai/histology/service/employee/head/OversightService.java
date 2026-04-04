package ru.mai.histology.service.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.mapper.ForensicCaseMapper;
import ru.mai.histology.mapper.SampleMapper;
import ru.mai.histology.models.ForensicCase;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.ForensicCaseRepository;
import ru.mai.histology.repo.SampleRepository;

import java.util.List;
import java.util.Optional;

/** Сервис надзора для начальника БСМЭ (read-only просмотр всех дел и образцов) */
@Service
@Slf4j
public class OversightService {

    private final ForensicCaseRepository forensicCaseRepository;
    private final SampleRepository sampleRepository;

    public OversightService(ForensicCaseRepository forensicCaseRepository,
                            SampleRepository sampleRepository) {
        this.forensicCaseRepository = forensicCaseRepository;
        this.sampleRepository = sampleRepository;
    }

    // ========== Дела ==========

    @Transactional(readOnly = true)
    public List<ForensicCaseDTO> getAllCases() {
        List<ForensicCase> cases = forensicCaseRepository.findAllByOrderByReceiptDateDesc();
        return ForensicCaseMapper.INSTANCE.toDTOList(cases);
    }

    @Transactional(readOnly = true)
    public Optional<ForensicCaseDTO> getCaseById(Long id) {
        return forensicCaseRepository.findById(id)
                .map(ForensicCaseMapper.INSTANCE::toDTO);
    }

    // ========== Образцы ==========

    @Transactional(readOnly = true)
    public List<SampleDTO> getAllSamples() {
        List<Sample> samples = sampleRepository.findAllByOrderByReceiptDateDesc();
        return SampleMapper.INSTANCE.toDTOList(samples);
    }

    @Transactional(readOnly = true)
    public List<SampleDTO> getSamplesByCase(Long caseId) {
        List<Sample> samples = sampleRepository.findAllByForensicCaseIdOrderBySampleNumberAsc(caseId);
        return SampleMapper.INSTANCE.toDTOList(samples);
    }

    @Transactional(readOnly = true)
    public Optional<SampleDTO> getSampleById(Long id) {
        return sampleRepository.findById(id)
                .map(SampleMapper.INSTANCE::toDTO);
    }
}
