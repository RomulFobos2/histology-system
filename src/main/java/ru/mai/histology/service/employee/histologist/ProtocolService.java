package ru.mai.histology.service.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mai.histology.dto.ResearchProtocolDTO;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.mapper.ResearchProtocolMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ResearchProtocol;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.HistologistConclusionRepository;
import ru.mai.histology.repo.ResearchProtocolRepository;
import ru.mai.histology.repo.SampleRepository;
import ru.mai.histology.service.general.ActionLogService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Сервис управления протоколами исследования */
@Service
@Slf4j
public class ProtocolService {

    /** Префикс для номера протокола (кириллическая П). */
    public static final String PROTOCOL_NUMBER_PREFIX = "П";

    /** Формат даты в номере: например, "25-05-26". */
    private static final DateTimeFormatter PROTOCOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yy");

    /** Сколько раз пытаться сохранить при гонке (UNIQUE-collision). */
    private static final int SAVE_RETRY_LIMIT = 5;

    private final ResearchProtocolRepository protocolRepository;
    private final SampleRepository sampleRepository;
    private final EmployeeRepository employeeRepository;
    private final HistologistConclusionRepository conclusionRepository;
    private final ActionLogService actionLogService;
    private final TransactionTemplate txTemplate;

    public ProtocolService(ResearchProtocolRepository protocolRepository,
                           SampleRepository sampleRepository,
                           EmployeeRepository employeeRepository,
                           HistologistConclusionRepository conclusionRepository,
                           ActionLogService actionLogService,
                           PlatformTransactionManager transactionManager) {
        this.protocolRepository = protocolRepository;
        this.sampleRepository = sampleRepository;
        this.employeeRepository = employeeRepository;
        this.conclusionRepository = conclusionRepository;
        this.actionLogService = actionLogService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // ========== Автогенерация номера ==========

    /** Префикс «П-dd-MM-yy» для номера протокола на указанную дату. */
    public String buildDatePrefix(LocalDate date) {
        return PROTOCOL_NUMBER_PREFIX + "-" + date.format(PROTOCOL_DATE_FORMATTER);
    }

    /**
     * Возвращает следующий по порядку номер протокола для указанной даты:
     * MAX(N) среди существующих {prefix}/N + 1. Если протоколов за дату ещё
     * нет — возвращает {prefix}/1. Не резервирует номер — это превью.
     */
    @Transactional(readOnly = true)
    public String previewNextProtocolNumber(LocalDate date) {
        String prefix = buildDatePrefix(date);
        Integer max = protocolRepository.findMaxSequenceForPrefix(prefix);
        int next = (max == null ? 0 : max) + 1;
        return prefix + "/" + next;
    }

    /** Сокращение: превью на текущую серверную дату. */
    public String previewNextProtocolNumber() {
        return previewNextProtocolNumber(LocalDate.now());
    }

    // ========== Чтение ==========

    @Transactional(readOnly = true)
    public List<ResearchProtocolDTO> getAllProtocols() {
        List<ResearchProtocol> protocols = protocolRepository.findAllByOrderByCreatedDateDesc();
        return ResearchProtocolMapper.INSTANCE.toDTOList(protocols);
    }

    /** Протоколы, созданные текущим гистологом. */
    @Transactional(readOnly = true)
    public List<ResearchProtocolDTO> getProtocolsByCurrentHistologist() {
        Employee currentUser = getCurrentUser();
        if (currentUser == null) return List.of();
        List<ResearchProtocol> protocols =
                protocolRepository.findAllByCreatedByIdOrderByCreatedDateDesc(currentUser.getId());
        return ResearchProtocolMapper.INSTANCE.toDTOList(protocols);
    }

    /** Создан ли протокол текущим гистологом. */
    @Transactional(readOnly = true)
    public boolean isAuthoredByCurrentUser(Long protocolId) {
        Employee currentUser = getCurrentUser();
        if (currentUser == null) return false;
        return protocolRepository.findById(protocolId)
                .map(p -> p.getCreatedBy() != null
                        && p.getCreatedBy().getId().equals(currentUser.getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<ResearchProtocolDTO> getProtocolById(Long id) {
        return protocolRepository.findById(id)
                .map(ResearchProtocolMapper.INSTANCE::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<ResearchProtocolDTO> getProtocolBySampleId(Long sampleId) {
        return protocolRepository.findBySampleId(sampleId)
                .map(ResearchProtocolMapper.INSTANCE::toDTO);
    }

    // ========== Генерация протокола ==========

    /**
     * Создаёт новый протокол исследования. protocolNumber и createdDate
     * генерируются сервером и НЕ принимаются из формы — защита от ручной
     * правки HTML и от попыток отправить произвольные значения через POST.
     * При коллизии UNIQUE(protocolNumber) повторяет попытку до SAVE_RETRY_LIMIT
     * раз — на случай одновременной генерации с другим пользователем.
     */
    public Optional<Long> generateProtocol(Long sampleId, String protocolText) {
        log.info("Генерация протокола для образца: sampleId={} (автогенерация номера)", sampleId);

        // Проверки бизнес-правил выносим из retry-цикла: они не зависят
        // от попыток и должны падать сразу.
        Optional<Sample> sampleOpt = sampleRepository.findById(sampleId);
        if (sampleOpt.isEmpty()) {
            log.error("Образец не найден: id={}", sampleId);
            return Optional.empty();
        }
        if (protocolRepository.existsBySampleId(sampleId)) {
            log.error("Протокол для образца id={} уже существует", sampleId);
            return Optional.empty();
        }
        Employee currentUser = getCurrentUser();
        if (currentUser == null) {
            log.error("Не удалось определить текущего пользователя");
            return Optional.empty();
        }

        for (int attempt = 1; attempt <= SAVE_RETRY_LIMIT; attempt++) {
            try {
                Long savedId = txTemplate.execute(status -> {
                    LocalDate createdDate = LocalDate.now();
                    String protocolNumber = previewNextProtocolNumber(createdDate);

                    ResearchProtocol protocol = new ResearchProtocol();
                    protocol.setProtocolNumber(protocolNumber);
                    protocol.setCreatedDate(createdDate);
                    protocol.setProtocolText(protocolText);
                    protocol.setSample(sampleOpt.get());
                    protocol.setCreatedBy(currentUser);

                    protocolRepository.saveAndFlush(protocol);
                    log.info("Протокол сохранён: id={}, protocolNumber={}", protocol.getId(), protocolNumber);
                    actionLogService.log(ActionType.PROTOCOL_CREATED, "ResearchProtocol", protocol.getId(),
                            "Создан протокол " + protocolNumber + " по образцу №" + sampleOpt.get().getSampleNumber());
                    return protocol.getId();
                });
                return Optional.ofNullable(savedId);
            } catch (DataIntegrityViolationException e) {
                log.warn("UNIQUE-collision при сохранении протокола, попытка {}/{}", attempt, SAVE_RETRY_LIMIT);
                if (attempt == SAVE_RETRY_LIMIT) {
                    log.error("Не удалось сгенерировать уникальный номер протокола за {} попыток", SAVE_RETRY_LIMIT);
                }
            } catch (Exception e) {
                log.error("Ошибка при генерации протокола: {}", e.getMessage(), e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ========== Редактирование ==========

    /**
     * Редактирование протокола. protocolNumber и createdDate ВСЕГДА игнорируются
     * из формы и берутся из существующей записи — эти поля неизменяемы.
     */
    @Transactional
    public Optional<Long> editProtocol(Long id, String protocolText) {
        log.info("Редактирование протокола: id={}", id);

        Optional<ResearchProtocol> protocolOpt = protocolRepository.findById(id);
        if (protocolOpt.isEmpty()) {
            log.error("Протокол не найден: id={}", id);
            return Optional.empty();
        }

        try {
            ResearchProtocol protocol = protocolOpt.get();
            // protocolNumber и createdDate не трогаем — они сгенерированы при создании
            protocol.setProtocolText(protocolText);

            protocolRepository.save(protocol);
            log.info("Протокол обновлён: id={}", id);
            actionLogService.log(ActionType.PROTOCOL_UPDATED, "ResearchProtocol", id,
                    "Изменён протокол " + protocol.getProtocolNumber());
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании протокола: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    // ========== Проверки ==========

    public boolean checkProtocolNumber(String protocolNumber) {
        return protocolRepository.existsByProtocolNumber(protocolNumber);
    }

    public boolean checkProtocolNumberExcluding(String protocolNumber, Long id) {
        return protocolRepository.existsByProtocolNumberAndIdNot(protocolNumber, id);
    }

    public boolean protocolExistsForSample(Long sampleId) {
        return protocolRepository.existsBySampleId(sampleId);
    }

    // ========== Генерация текста протокола ==========

    /** Генерирует шаблон текста протокола на основе данных образца и заключения */
    @Transactional(readOnly = true)
    public String generateProtocolText(Long sampleId) {
        Optional<Sample> sampleOpt = sampleRepository.findById(sampleId);
        if (sampleOpt.isEmpty()) return "";

        Sample sample = sampleOpt.get();
        StringBuilder sb = new StringBuilder();

        sb.append("ПРОТОКОЛ ГИСТОЛОГИЧЕСКОГО ИССЛЕДОВАНИЯ\n\n");
        sb.append("Дело № ").append(sample.getForensicCase().getCaseNumber()).append("\n");
        sb.append("Образец № ").append(sample.getSampleNumber()).append("\n");
        sb.append("Дата поступления: ").append(sample.getReceiptDate()).append("\n\n");

        sb.append("1. МАТЕРИАЛ ИССЛЕДОВАНИЯ\n");
        sb.append("Тип ткани: ").append(sample.getTissueType().getDisplayName()).append("\n");
        sb.append("Метод окрашивания: ").append(sample.getStainingMethod().getDisplayName()).append("\n\n");

        sb.append("2. МЕТОДИКА ИССЛЕДОВАНИЯ\n");
        sb.append("Материал фиксирован в 10% нейтральном формалине, ");
        sb.append("обработан по стандартной гистологической методике, ");
        sb.append("залит в парафин. Срезы толщиной 5-7 мкм окрашены ");
        sb.append(sample.getStainingMethod().getDisplayName()).append(".\n\n");

        sb.append("3. МИКРОСКОПИЧЕСКОЕ ОПИСАНИЕ\n");

        // Если есть заключение гистолога — подставить описание
        conclusionRepository.findBySampleId(sampleId).ifPresent(conclusion -> {
            sb.append(conclusion.getMicroscopicDescription()).append("\n\n");
            sb.append("4. ЗАКЛЮЧЕНИЕ\n");
            sb.append(conclusion.getDiagnosis()).append("\n");
            sb.append(conclusion.getConclusionText()).append("\n");
        });

        if (!conclusionRepository.existsBySampleId(sampleId)) {
            sb.append("[Заполнить микроскопическое описание]\n\n");
            sb.append("4. ЗАКЛЮЧЕНИЕ\n");
            sb.append("[Заполнить заключение]\n");
        }

        return sb.toString();
    }

    // ========== Вспомогательные ==========

    private Employee getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByUsername(username).orElse(null);
    }
}
