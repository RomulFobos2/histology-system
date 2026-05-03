package ru.mai.histology.service.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.histology.dto.HistologistConclusionDTO;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.mapper.HistologistConclusionMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.HistologistConclusion;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.HistologistConclusionRepository;
import ru.mai.histology.repo.SampleRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Сервис управления заключениями гистолога */
@Service
@Slf4j
public class ConclusionService {

    private final HistologistConclusionRepository conclusionRepository;
    private final SampleRepository sampleRepository;
    private final EmployeeRepository employeeRepository;

    public ConclusionService(HistologistConclusionRepository conclusionRepository,
                             SampleRepository sampleRepository,
                             EmployeeRepository employeeRepository) {
        this.conclusionRepository = conclusionRepository;
        this.sampleRepository = sampleRepository;
        this.employeeRepository = employeeRepository;
    }

    // ========== Чтение ==========

    @Transactional(readOnly = true)
    public List<HistologistConclusionDTO> getAllConclusions() {
        List<HistologistConclusion> conclusions = conclusionRepository.findAllByOrderByConclusionDateDesc();
        return HistologistConclusionMapper.INSTANCE.toDTOList(conclusions);
    }

    @Transactional(readOnly = true)
    public List<HistologistConclusionDTO> getConclusionsByCurrentHistologist() {
        Employee currentUser = getCurrentUser();
        if (currentUser == null) return List.of();
        List<HistologistConclusion> conclusions =
                conclusionRepository.findAllByHistologistIdOrderByConclusionDateDesc(currentUser.getId());
        return HistologistConclusionMapper.INSTANCE.toDTOList(conclusions);
    }

    @Transactional(readOnly = true)
    public Optional<HistologistConclusionDTO> getConclusionById(Long id) {
        return conclusionRepository.findById(id)
                .map(HistologistConclusionMapper.INSTANCE::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<HistologistConclusionDTO> getConclusionBySampleId(Long sampleId) {
        return conclusionRepository.findBySampleId(sampleId)
                .map(HistologistConclusionMapper.INSTANCE::toDTO);
    }

    // ========== Создание ==========

    @Transactional
    public Optional<Long> saveConclusion(Long sampleId, String microscopicDescription,
                                         String diagnosis, String conclusionText) {
        log.info("Создание заключения гистолога для образца: sampleId={}", sampleId);

        Optional<Sample> sampleOpt = sampleRepository.findById(sampleId);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", sampleId);
            return Optional.empty();
        }

        if (conclusionRepository.existsBySampleId(sampleId)) {
            log.error("Заключение для образца id={} уже существует", sampleId);
            return Optional.empty();
        }

        Employee currentUser = getCurrentUser();
        if (currentUser == null) {
            log.error("Не удалось определить текущего пользователя");
            return Optional.empty();
        }

        try {
            HistologistConclusion conclusion = new HistologistConclusion();
            conclusion.setMicroscopicDescription(microscopicDescription);
            conclusion.setDiagnosis(diagnosis);
            conclusion.setConclusionText(conclusionText);
            conclusion.setConclusionDate(LocalDate.now());
            conclusion.setSample(sampleOpt.get());
            conclusion.setHistologist(currentUser);

            conclusionRepository.save(conclusion);

            // Обновляем статус образца → ANALYZED
            Sample sample = sampleOpt.get();
            if (sample.getStatus() == SampleStatus.AWAITING_ANALYSIS
                    || sample.getStatus() == SampleStatus.IN_PROGRESS) {
                sample.setStatus(SampleStatus.ANALYZED);
                sampleRepository.save(sample);
                log.info("Статус образца id={} обновлён на ANALYZED", sampleId);
            }

            log.info("Заключение гистолога сохранено: id={}, sampleId={}", conclusion.getId(), sampleId);
            return Optional.of(conclusion.getId());
        } catch (Exception e) {
            log.error("Ошибка при сохранении заключения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Редактирование ==========

    @Transactional
    public Optional<Long> editConclusion(Long id, String microscopicDescription,
                                         String diagnosis, String conclusionText) {
        log.info("Редактирование заключения гистолога: id={}", id);

        Optional<HistologistConclusion> conclusionOpt = conclusionRepository.findById(id);
        if (conclusionOpt.isEmpty()) {
            log.error("Заключение не найдено: id={}", id);
            return Optional.empty();
        }

        try {
            HistologistConclusion conclusion = conclusionOpt.get();
            conclusion.setMicroscopicDescription(microscopicDescription);
            conclusion.setDiagnosis(diagnosis);
            conclusion.setConclusionText(conclusionText);

            conclusionRepository.save(conclusion);
            log.info("Заключение гистолога обновлено: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании заключения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Удаление ==========

    @Transactional
    public boolean deleteConclusion(Long id) {
        log.info("Удаление заключения гистолога: id={}", id);

        if (!conclusionRepository.existsById(id)) {
            log.error("Заключение не найдено: id={}", id);
            return false;
        }

        try {
            // Восстанавливаем статус образца на AWAITING_ANALYSIS
            HistologistConclusion conclusion = conclusionRepository.findById(id).get();
            Sample sample = conclusion.getSample();
            if (sample.getStatus() == SampleStatus.ANALYZED) {
                sample.setStatus(SampleStatus.AWAITING_ANALYSIS);
                sampleRepository.save(sample);
                log.info("Статус образца id={} возвращён на AWAITING_ANALYSIS", sample.getId());
            }

            conclusionRepository.deleteById(id);
            log.info("Заключение гистолога удалено: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении заключения: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Проверки ==========

    public boolean conclusionExistsForSample(Long sampleId) {
        return conclusionRepository.existsBySampleId(sampleId);
    }

    /** Создано ли заключение текущим гистологом. */
    @Transactional(readOnly = true)
    public boolean isAuthoredByCurrentUser(Long conclusionId) {
        Employee currentUser = getCurrentUser();
        if (currentUser == null) return false;
        return conclusionRepository.findById(conclusionId)
                .map(c -> c.getHistologist() != null
                        && c.getHistologist().getId().equals(currentUser.getId()))
                .orElse(false);
    }

    // ========== Вспомогательные ==========

    private Employee getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByUsername(username).orElse(null);
    }
}
