package ru.mai.histology.service.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.mapper.ForensicCaseMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ForensicCase;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.ForensicCaseRepository;
import ru.mai.histology.repo.SampleRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ForensicCaseService {

    private final ForensicCaseRepository forensicCaseRepository;
    private final EmployeeRepository employeeRepository;
    private final SampleRepository sampleRepository;

    public ForensicCaseService(ForensicCaseRepository forensicCaseRepository,
                               EmployeeRepository employeeRepository,
                               SampleRepository sampleRepository) {
        this.forensicCaseRepository = forensicCaseRepository;
        this.employeeRepository = employeeRepository;
        this.sampleRepository = sampleRepository;
    }

    // ========== CRUD ==========

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

    @Transactional
    public Optional<Long> saveCase(String caseNumber, LocalDate receiptDate,
                                   String description, Long expertId) {
        log.info("Сохранение нового дела: caseNumber={}", caseNumber);

        if (forensicCaseRepository.existsByCaseNumber(caseNumber)) {
            log.error("Дело с номером {} уже существует", caseNumber);
            return Optional.empty();
        }

        try {
            ForensicCase forensicCase = new ForensicCase();
            forensicCase.setCaseNumber(caseNumber);
            forensicCase.setReceiptDate(receiptDate);
            forensicCase.setDescription(description);
            forensicCase.setStatus(CaseStatus.OPEN);

            if (expertId != null) {
                Optional<Employee> expertOpt = employeeRepository.findById(expertId);
                expertOpt.ifPresent(forensicCase::setResponsibleExpert);
            }

            forensicCaseRepository.save(forensicCase);
            log.info("Дело сохранено: id={}, caseNumber={}", forensicCase.getId(), caseNumber);
            return Optional.of(forensicCase.getId());
        } catch (Exception e) {
            log.error("Ошибка при сохранении дела: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editCase(Long id, String caseNumber, LocalDate receiptDate,
                                   String description, Long expertId) {
        log.info("Редактирование дела: id={}", id);

        Optional<ForensicCase> caseOptional = forensicCaseRepository.findById(id);
        if (caseOptional.isEmpty()) {
            log.error("Дело не найдено: id={}", id);
            return Optional.empty();
        }

        if (forensicCaseRepository.existsByCaseNumberAndIdNot(caseNumber, id)) {
            log.error("Дело с номером {} уже существует", caseNumber);
            return Optional.empty();
        }

        try {
            ForensicCase forensicCase = caseOptional.get();
            forensicCase.setCaseNumber(caseNumber);
            forensicCase.setReceiptDate(receiptDate);
            forensicCase.setDescription(description);

            if (expertId != null) {
                Optional<Employee> expertOpt = employeeRepository.findById(expertId);
                expertOpt.ifPresent(forensicCase::setResponsibleExpert);
            } else {
                forensicCase.setResponsibleExpert(null);
            }

            forensicCaseRepository.save(forensicCase);
            log.info("Дело обновлено: id={}", id);
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
            forensicCaseRepository.deleteById(id);
            log.info("Дело удалено: id={}", id);
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
