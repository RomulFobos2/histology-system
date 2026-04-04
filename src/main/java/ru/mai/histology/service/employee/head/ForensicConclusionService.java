package ru.mai.histology.service.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.histology.dto.ForensicConclusionDTO;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.mapper.ForensicConclusionMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ForensicCase;
import ru.mai.histology.models.ForensicConclusion;
import ru.mai.histology.models.HistologistConclusion;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.ForensicConclusionRepository;
import ru.mai.histology.repo.HistologistConclusionRepository;
import ru.mai.histology.repo.ForensicCaseRepository;
import ru.mai.histology.repo.SampleRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Сервис управления судебно-медицинскими заключениями (начальник БСМЭ) */
@Service
@Slf4j
public class ForensicConclusionService {

    private final ForensicConclusionRepository conclusionRepository;
    private final SampleRepository sampleRepository;
    private final EmployeeRepository employeeRepository;
    private final HistologistConclusionRepository histConclusionRepository;
    private final ForensicCaseRepository forensicCaseRepository;

    public ForensicConclusionService(ForensicConclusionRepository conclusionRepository,
                                     SampleRepository sampleRepository,
                                     EmployeeRepository employeeRepository,
                                     HistologistConclusionRepository histConclusionRepository,
                                     ForensicCaseRepository forensicCaseRepository) {
        this.conclusionRepository = conclusionRepository;
        this.sampleRepository = sampleRepository;
        this.employeeRepository = employeeRepository;
        this.histConclusionRepository = histConclusionRepository;
        this.forensicCaseRepository = forensicCaseRepository;
    }

    // ========== Чтение ==========

    @Transactional(readOnly = true)
    public List<ForensicConclusionDTO> getAllConclusions() {
        List<ForensicConclusion> conclusions = conclusionRepository.findAllByOrderByConclusionDateDesc();
        List<ForensicConclusionDTO> dtos = ForensicConclusionMapper.INSTANCE.toDTOList(conclusions);

        // Дополняем данными заключения гистолога
        for (int i = 0; i < conclusions.size(); i++) {
            enrichWithHistologistData(dtos.get(i), conclusions.get(i).getSample().getId());
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public Optional<ForensicConclusionDTO> getConclusionById(Long id) {
        Optional<ForensicConclusion> conclusionOpt = conclusionRepository.findById(id);
        if (conclusionOpt.isEmpty()) return Optional.empty();

        ForensicConclusionDTO dto = ForensicConclusionMapper.INSTANCE.toDTO(conclusionOpt.get());
        enrichWithHistologistData(dto, conclusionOpt.get().getSample().getId());
        return Optional.of(dto);
    }

    @Transactional(readOnly = true)
    public Optional<ForensicConclusionDTO> getConclusionBySampleId(Long sampleId) {
        Optional<ForensicConclusion> conclusionOpt = conclusionRepository.findBySampleId(sampleId);
        if (conclusionOpt.isEmpty()) return Optional.empty();

        ForensicConclusionDTO dto = ForensicConclusionMapper.INSTANCE.toDTO(conclusionOpt.get());
        enrichWithHistologistData(dto, sampleId);
        return Optional.of(dto);
    }

    // ========== Создание ==========

    @Transactional
    public Optional<Long> saveConclusion(Long sampleId, String conclusionText, boolean isFinal) {
        log.info("Создание судебно-медицинского заключения для образца: sampleId={}", sampleId);

        Optional<Sample> sampleOpt = sampleRepository.findById(sampleId);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", sampleId);
            return Optional.empty();
        }

        if (conclusionRepository.existsBySampleId(sampleId)) {
            log.error("Судебно-медицинское заключение для образца id={} уже существует", sampleId);
            return Optional.empty();
        }

        Employee currentUser = getCurrentUser();
        if (currentUser == null) {
            log.error("Не удалось определить текущего пользователя");
            return Optional.empty();
        }

        try {
            ForensicConclusion conclusion = new ForensicConclusion();
            conclusion.setConclusionText(conclusionText);
            conclusion.setConclusionDate(LocalDate.now());
            conclusion.setFinal(isFinal);
            conclusion.setSample(sampleOpt.get());
            conclusion.setHead(currentUser);

            conclusionRepository.save(conclusion);

            // Обновляем статус образца → CONCLUDED
            Sample sample = sampleOpt.get();
            if (isFinal) {
                sample.setStatus(SampleStatus.CONCLUDED);
                sampleRepository.save(sample);
                log.info("Статус образца id={} обновлён на CONCLUDED", sampleId);

                // Пересчитываем статус дела
                recalculateCaseStatus(sample.getForensicCase());
            }

            log.info("Судебно-медицинское заключение сохранено: id={}, sampleId={}", conclusion.getId(), sampleId);
            return Optional.of(conclusion.getId());
        } catch (Exception e) {
            log.error("Ошибка при сохранении заключения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Редактирование ==========

    @Transactional
    public Optional<Long> editConclusion(Long id, String conclusionText, boolean isFinal) {
        log.info("Редактирование судебно-медицинского заключения: id={}", id);

        Optional<ForensicConclusion> conclusionOpt = conclusionRepository.findById(id);
        if (conclusionOpt.isEmpty()) {
            log.error("Заключение не найдено: id={}", id);
            return Optional.empty();
        }

        try {
            ForensicConclusion conclusion = conclusionOpt.get();
            boolean wasFinal = conclusion.isFinal();
            conclusion.setConclusionText(conclusionText);
            conclusion.setFinal(isFinal);

            conclusionRepository.save(conclusion);

            // Если изменился флаг isFinal — пересчитываем статусы
            Sample sample = conclusion.getSample();
            if (isFinal && !wasFinal) {
                sample.setStatus(SampleStatus.CONCLUDED);
                sampleRepository.save(sample);
                recalculateCaseStatus(sample.getForensicCase());
            } else if (!isFinal && wasFinal) {
                sample.setStatus(SampleStatus.ANALYZED);
                sampleRepository.save(sample);
                recalculateCaseStatus(sample.getForensicCase());
            }

            log.info("Судебно-медицинское заключение обновлено: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании заключения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Проверки ==========

    public boolean conclusionExistsForSample(Long sampleId) {
        return conclusionRepository.existsBySampleId(sampleId);
    }

    // ========== Вспомогательные ==========

    /** Дополняет DTO данными из заключения гистолога */
    private void enrichWithHistologistData(ForensicConclusionDTO dto, Long sampleId) {
        histConclusionRepository.findBySampleId(sampleId).ifPresent(hc -> {
            dto.setHistologistDiagnosis(hc.getDiagnosis());
            dto.setHistologistConclusionText(hc.getConclusionText());
            Employee histologist = hc.getHistologist();
            if (histologist != null) {
                String fullName = histologist.getLastName() + " " + histologist.getFirstName() +
                        (histologist.getMiddleName() != null && !histologist.getMiddleName().isEmpty()
                                ? " " + histologist.getMiddleName() : "");
                dto.setHistologistFullName(fullName);
            }
        });
    }

    /** Пересчитывает статус дела на основе статусов образцов */
    private void recalculateCaseStatus(ForensicCase forensicCase) {
        ForensicCase fc = forensicCaseRepository.findById(forensicCase.getId()).orElse(null);
        if (fc == null) return;

        List<Sample> samples = sampleRepository.findAllByForensicCaseIdOrderBySampleNumberAsc(fc.getId());
        if (samples.isEmpty()) return;

        boolean allConcluded = samples.stream()
                .allMatch(s -> s.getStatus() == SampleStatus.CONCLUDED);
        boolean anyConcluded = samples.stream()
                .anyMatch(s -> s.getStatus() == SampleStatus.CONCLUDED);

        if (allConcluded) {
            fc.setStatus(CaseStatus.CONCLUDED);
        } else if (anyConcluded) {
            fc.setStatus(CaseStatus.IN_PROGRESS);
        }

        forensicCaseRepository.save(fc);
        log.info("Статус дела id={} пересчитан: {}", fc.getId(), fc.getStatus());
    }

    private Employee getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByUsername(username).orElse(null);
    }
}
