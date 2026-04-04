package ru.mai.histology.service.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.mapper.SampleMapper;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.SampleRepository;

import java.util.List;
import java.util.Optional;

/** Сервис просмотра образцов для гистолога (read-only) */
@Service
@Slf4j
public class SampleViewService {

    private final SampleRepository sampleRepository;

    public SampleViewService(SampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    @Transactional(readOnly = true)
    public List<SampleDTO> getAllSamples() {
        List<Sample> samples = sampleRepository.findAllByOrderByReceiptDateDesc();
        return SampleMapper.INSTANCE.toDTOList(samples);
    }

    @Transactional(readOnly = true)
    public Optional<SampleDTO> getSampleById(Long id) {
        return sampleRepository.findById(id)
                .map(SampleMapper.INSTANCE::toDTO);
    }
}
