package ru.mai.histology.service.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.mapper.ForensicCaseMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ForensicCase;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.ForensicCaseRepository;
import ru.mai.histology.repo.SampleRepository;
import ru.mai.histology.service.general.ActionLogService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ForensicCaseService {

    /** Префикс для номера дела (кириллическая Д). */
    public static final String CASE_NUMBER_PREFIX = "Д";

    /** Формат даты в номере: например, "25-05-26". */
    private static final DateTimeFormatter CASE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yy");

    /** Сколько раз пытаться сохранить при гонке (UNIQUE-collision). */
    private static final int SAVE_RETRY_LIMIT = 5;

    private final ForensicCaseRepository forensicCaseRepository;
    private final EmployeeRepository employeeRepository;
    private final SampleRepository sampleRepository;
    private final ActionLogService actionLogService;
    private final TransactionTemplate txTemplate;

    public ForensicCaseService(ForensicCaseRepository forensicCaseRepository,
                               EmployeeRepository employeeRepository,
                               SampleRepository sampleRepository,
                               ActionLogService actionLogService,
                               PlatformTransactionManager transactionManager) {
        this.forensicCaseRepository = forensicCaseRepository;
        this.employeeRepository = employeeRepository;
        this.sampleRepository = sampleRepository;
        this.actionLogService = actionLogService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // ========== Чтение ==========

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

    // ========== Автогенерация номера ==========

    /**
     * Префикс «Д-dd-MM-yy» для номера дела на указанную дату.
     */
    public String buildDatePrefix(LocalDate date) {
        return CASE_NUMBER_PREFIX + "-" + date.format(CASE_DATE_FORMATTER);
    }

    /**
     * Возвращает следующий по порядку номер дела для указанной даты:
     * MAX(N) среди существующих {prefix}/N + 1. Если дел за дату ещё нет
     * — возвращает {prefix}/1. Не резервирует номер — это превью.
     */
    @Transactional(readOnly = true)
    public String previewNextCaseNumber(LocalDate date) {
        String prefix = buildDatePrefix(date);
        Integer max = forensicCaseRepository.findMaxSequenceForPrefix(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + "/" + next;
    }

    /** Сокращение: превью на текущую серверную дату. */
    public String previewNextCaseNumber() {
        return previewNextCaseNumber(LocalDate.now());
    }

    // ========== CRUD ==========

    /**
     * Создаёт новое судебное дело. caseNumber и receiptDate генерируются
     * сервером и НЕ принимаются из формы — это защита от ручной правки HTML
     * и от попыток отправить произвольные значения через POST.
     * При коллизии UNIQUE(caseNumber) повторяет попытку до SAVE_RETRY_LIMIT раз
     * — на случай одновременной регистрации с другим лаборантом.
     */
    public Optional<Long> saveCase(String description, Long expertId) {
        log.info("Сохранение нового дела (автогенерация номера)");

        for (int attempt = 1; attempt <= SAVE_RETRY_LIMIT; attempt++) {
            try {
                Long savedId = txTemplate.execute(status -> {
                    LocalDate receiptDate = LocalDate.now();
                    String caseNumber = previewNextCaseNumber(receiptDate);

                    ForensicCase forensicCase = new ForensicCase();
                    forensicCase.setCaseNumber(caseNumber);
                    forensicCase.setReceiptDate(receiptDate);
                    forensicCase.setDescription(description);
                    forensicCase.setStatus(CaseStatus.OPEN);

                    if (expertId != null) {
                        Optional<Employee> expertOpt = employeeRepository.findById(expertId);
                        expertOpt.ifPresent(forensicCase::setResponsibleExpert);
                    }

                    forensicCaseRepository.saveAndFlush(forensicCase);
                    log.info("Дело сохранено: id={}, caseNumber={}", forensicCase.getId(), caseNumber);
                    actionLogService.log(ActionType.CASE_CREATED, "ForensicCase", forensicCase.getId(),
                            "Создано дело " + caseNumber);
                    return forensicCase.getId();
                });
                return Optional.ofNullable(savedId);
            } catch (DataIntegrityViolationException e) {
                log.warn("UNIQUE-collision при сохранении дела, попытка {}/{}", attempt, SAVE_RETRY_LIMIT);
                if (attempt == SAVE_RETRY_LIMIT) {
                    log.error("Не удалось сгенерировать уникальный номер дела за {} попыток", SAVE_RETRY_LIMIT);
                }
            } catch (Exception e) {
                log.error("Ошибка при сохранении дела: {}", e.getMessage(), e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Редактирование дела. caseNumber и receiptDate ВСЕГДА игнорируются
     * из формы и берутся из существующей записи — эти поля неизменяемы.
     */
    @Transactional
    public Optional<Long> editCase(Long id, String description, Long expertId) {
        log.info("Редактирование дела: id={}", id);

        Optional<ForensicCase> caseOptional = forensicCaseRepository.findById(id);
        if (caseOptional.isEmpty()) {
            log.error("Дело не найдено: id={}", id);
            return Optional.empty();
        }

        try {
            ForensicCase forensicCase = caseOptional.get();
            // caseNumber и receiptDate не трогаем — они сгенерированы при создании
            forensicCase.setDescription(description);

            if (expertId != null) {
                Optional<Employee> expertOpt = employeeRepository.findById(expertId);
                expertOpt.ifPresent(forensicCase::setResponsibleExpert);
            } else {
                forensicCase.setResponsibleExpert(null);
            }

            forensicCaseRepository.save(forensicCase);
            log.info("Дело обновлено: id={}", id);
            actionLogService.log(ActionType.CASE_UPDATED, "ForensicCase", id,
                    "Изменено дело " + forensicCase.getCaseNumber());
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании дела: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deleteCase(Long id) {
        log.info("Удаление дела: id={}", id);

        Optional<ForensicCase> caseOptional = forensicCaseRepository.findById(id);
        if (caseOptional.isEmpty()) {
            log.error("Дело не найдено: id={}", id);
            return false;
        }

        if (sampleRepository.countByForensicCaseId(id) > 0) {
            log.error("Невозможно удалить дело id={}: есть связанные образцы", id);
            return false;
        }

        try {
            String caseNumber = caseOptional.get().getCaseNumber();
            forensicCaseRepository.deleteById(id);
            log.info("Дело удалено: id={}", id);
            actionLogService.log(ActionType.CASE_DELETED, "ForensicCase", id,
                    "Удалено дело " + caseNumber);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении дела: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Проверки ==========

    public boolean checkCaseNumber(String caseNumber) {
        return forensicCaseRepository.existsByCaseNumber(caseNumber);
    }

    public boolean checkCaseNumberExcluding(String caseNumber, Long id) {
        return forensicCaseRepository.existsByCaseNumberAndIdNot(caseNumber, id);
    }
}
