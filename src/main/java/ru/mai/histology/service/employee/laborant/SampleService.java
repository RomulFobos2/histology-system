package ru.mai.histology.service.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.ResearchStage;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.mapper.SampleMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ForensicCase;
import ru.mai.histology.repo.MicroscopeImageRepository;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.ForensicCaseRepository;
import ru.mai.histology.repo.SampleRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SampleService {

    private final SampleRepository sampleRepository;
    private final ForensicCaseRepository forensicCaseRepository;
    private final EmployeeRepository employeeRepository;
    private final MicroscopeImageRepository microscopeImageRepository;
    private ImageUploadService imageUploadService;

    public SampleService(SampleRepository sampleRepository,
                         ForensicCaseRepository forensicCaseRepository,
                         EmployeeRepository employeeRepository,
                         MicroscopeImageRepository microscopeImageRepository) {
        this.sampleRepository = sampleRepository;
        this.forensicCaseRepository = forensicCaseRepository;
        this.employeeRepository = employeeRepository;
        this.microscopeImageRepository = microscopeImageRepository;
    }

    /** Lazy-инъекция для избежания циклической зависимости */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setImageUploadService(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    // ========== CRUD ==========

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

    @Transactional
    public Optional<Long> saveSample(Long caseId, String sampleNumber, TissueType tissueType,
                                     StainingMethod stainingMethod, Long histologistId, String notes) {
        log.info("Сохранение нового образца: caseId={}, sampleNumber={}", caseId, sampleNumber);

        Optional<ForensicCase> caseOpt = forensicCaseRepository.findById(caseId);
        if (caseOpt.isEmpty()) {
            log.error("Дело не найдено: id={}", caseId);
            return Optional.empty();
        }

        if (sampleRepository.existsByForensicCaseIdAndSampleNumber(caseId, sampleNumber)) {
            log.error("Образец с номером {} уже существует в деле id={}", sampleNumber, caseId);
            return Optional.empty();
        }

        try {
            Sample sample = new Sample();
            sample.setSampleNumber(sampleNumber);
            sample.setReceiptDate(LocalDate.now());
            sample.setTissueType(tissueType);
            sample.setStainingMethod(stainingMethod);
            sample.setResearchStage(ResearchStage.RECEIVED);
            sample.setStatus(SampleStatus.NEW);
            sample.setNotes(notes);
            sample.setForensicCase(caseOpt.get());

            // registeredBy — текущий пользователь
            Employee currentUser = employeeRepository.findByUsername(
                    SecurityContextHolder.getContext().getAuthentication().getName()).orElse(null);
            sample.setRegisteredBy(currentUser);

            if (histologistId != null) {
                employeeRepository.findById(histologistId).ifPresent(sample::setAssignedHistologist);
            }

            sampleRepository.save(sample);
            log.info("Образец сохранён: id={}, sampleNumber={}", sample.getId(), sampleNumber);
            return Optional.of(sample.getId());
        } catch (Exception e) {
            log.error("Ошибка при сохранении образца: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editSample(Long id, String sampleNumber, TissueType tissueType,
                                     StainingMethod stainingMethod, Long histologistId, String notes) {
        log.info("Редактирование образца: id={}", id);

        Optional<Sample> sampleOpt = sampleRepository.findById(id);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", id);
            return Optional.empty();
        }

        Sample sample = sampleOpt.get();

        if (sampleRepository.existsByForensicCaseIdAndSampleNumberAndIdNot(
                sample.getForensicCase().getId(), sampleNumber, id)) {
            log.error("Образец с номером {} уже существует в деле", sampleNumber);
            return Optional.empty();
        }

        try {
            sample.setSampleNumber(sampleNumber);
            sample.setTissueType(tissueType);
            sample.setStainingMethod(stainingMethod);
            sample.setNotes(notes);

            if (histologistId != null) {
                employeeRepository.findById(histologistId).ifPresent(sample::setAssignedHistologist);
            } else {
                sample.setAssignedHistologist(null);
            }

            sampleRepository.save(sample);
            log.info("Образец обновлён: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании образца: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteSample(Long id) {
        log.info("Удаление образца: id={}", id);

        Optional<Sample> sampleOpt = sampleRepository.findById(id);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", id);
            return false;
        }

        try {
            // Каскадное удаление изображений (файлы + БД)
            if (imageUploadService != null) {
                imageUploadService.deleteAllImagesBySample(id);
            }

            sampleRepository.deleteById(id);
            log.info("Образец удалён: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении образца: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Transactional
    public boolean advanceStage(Long id) {
        log.info("Продвижение стадии образца: id={}", id);

        Optional<Sample> sampleOpt = sampleRepository.findById(id);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", id);
            return false;
        }

        Sample sample = sampleOpt.get();
        ResearchStage currentStage = sample.getResearchStage();

        if (currentStage == ResearchStage.COMPLETED) {
            log.warn("Образец id={} уже на финальной стадии", id);
            return false;
        }

        try {
            ResearchStage nextStage = ResearchStage.values()[currentStage.ordinal() + 1];
            sample.setResearchStage(nextStage);

            if (sample.getStatus() == SampleStatus.NEW) {
                sample.setStatus(SampleStatus.IN_PROGRESS);
            }

            if (nextStage == ResearchStage.MICROSCOPY) {
                sample.setStatus(SampleStatus.AWAITING_ANALYSIS);
            }

            sampleRepository.save(sample);
            log.info("Образец id={} переведён на стадию {}", id, nextStage);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при продвижении стадии: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Проверки ==========

    public boolean checkSampleNumber(Long caseId, String sampleNumber) {
        return sampleRepository.existsByForensicCaseIdAndSampleNumber(caseId, sampleNumber);
    }

    public boolean checkSampleNumberExcluding(Long caseId, String sampleNumber, Long id) {
        return sampleRepository.existsByForensicCaseIdAndSampleNumberAndIdNot(caseId, sampleNumber, id);
    }
}
