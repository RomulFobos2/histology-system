package ru.mai.histology.service.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.mapper.SampleMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.SampleRepository;

import java.util.List;
import java.util.Optional;

/** Сервис просмотра образцов для гистолога (read-only) */
@Service
@Slf4j
public class SampleViewService {

    private final SampleRepository sampleRepository;
    private final EmployeeRepository employeeRepository;

    public SampleViewService(SampleRepository sampleRepository,
                             EmployeeRepository employeeRepository) {
        this.sampleRepository = sampleRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<SampleDTO> getAllSamples() {
        List<Sample> samples = sampleRepository.findAllByOrderByReceiptDateDesc();
        return SampleMapper.INSTANCE.toDTOList(samples);
    }

    /** Образцы, назначенные текущему гистологу. */
    @Transactional(readOnly = true)
    public List<SampleDTO> getMySamples() {
        Employee currentUser = getCurrentUser();
        if (currentUser == null) return List.of();
        List<Sample> samples =
                sampleRepository.findAllByAssignedHistologistIdOrderByReceiptDateDesc(currentUser.getId());
        return SampleMapper.INSTANCE.toDTOList(samples);
    }

    @Transactional(readOnly = true)
    public Optional<SampleDTO> getSampleById(Long id) {
        return sampleRepository.findById(id)
                .map(SampleMapper.INSTANCE::toDTO);
    }

    /** Назначен ли образец текущему гистологу. */
    @Transactional(readOnly = true)
    public boolean isAssignedToCurrentUser(Long sampleId) {
        Employee currentUser = getCurrentUser();
        if (currentUser == null) return false;
        return sampleRepository.findById(sampleId)
                .map(s -> s.getAssignedHistologist() != null
                        && s.getAssignedHistologist().getId().equals(currentUser.getId()))
                .orElse(false);
    }

    private Employee getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByUsername(username).orElse(null);
    }
}
