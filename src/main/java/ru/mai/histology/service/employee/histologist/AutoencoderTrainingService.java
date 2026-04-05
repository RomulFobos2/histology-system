package ru.mai.histology.service.employee.histologist;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.TrainingSessionDTO;
import ru.mai.histology.mapper.TrainingSessionMapper;
import ru.mai.histology.models.AutoencoderModel;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.TrainingSession;
import ru.mai.histology.repo.AutoencoderModelRepository;
import ru.mai.histology.repo.TrainingSessionRepository;
import ru.mai.histology.service.employee.EmployeeService;
import ru.mai.histology.service.general.AutoencoderClientService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
@Getter
@Slf4j
public class AutoencoderTrainingService {

    private static final DateTimeFormatter PYTHON_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AutoencoderClientService autoencoderClientService;
    private final EmployeeService employeeService;
    private final TrainingSessionRepository trainingSessionRepository;
    private final AutoencoderModelRepository autoencoderModelRepository;

    public AutoencoderTrainingService(AutoencoderClientService autoencoderClientService,
                                      EmployeeService employeeService,
                                      TrainingSessionRepository trainingSessionRepository,
                                      AutoencoderModelRepository autoencoderModelRepository) {
        this.autoencoderClientService = autoencoderClientService;
        this.employeeService = employeeService;
        this.trainingSessionRepository = trainingSessionRepository;
        this.autoencoderModelRepository = autoencoderModelRepository;
    }

    public boolean isServiceAvailable() {
        return autoencoderClientService.isServiceAvailable();
    }

    public Map<String, Object> resetTrainingStatus() {
        return autoencoderClientService.resetTrainingStatus();
    }

    public Map<String, Object> getMetrics() {
        return autoencoderClientService.getMetrics();
    }

    public Map<String, Object> getTrainingStatus() {
        return autoencoderClientService.getTrainingStatus();
    }

    public List<Map<String, Object>> getModels() {
        return autoencoderClientService.getModels();
    }

    public List<Map<String, Object>> getPythonTrainingHistory() {
        return autoencoderClientService.getTrainingHistory();
    }

    /**
     * Активирует модель по имени. Деактивирует все остальные.
     * Для baseline-модели, которой может не быть в БД, создаёт запись.
     */
    @Transactional
    public boolean activateModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }

        // Деактивировать все модели
        List<AutoencoderModel> allModels = autoencoderModelRepository.findAll();
        for (AutoencoderModel m : allModels) {
            m.setActive(false);
            autoencoderModelRepository.save(m);
        }

        // Найти или создать нужную
        AutoencoderModel target = autoencoderModelRepository.findByModelName(modelName)
                .orElseGet(() -> {
                    AutoencoderModel newModel = new AutoencoderModel();
                    newModel.setModelName(modelName);
                    newModel.setDescription(modelName.contains("baseline")
                            ? "Базовый пайплайн улучшения на Pillow"
                            : "Нейросетевая модель улучшения");
                    newModel.setTrainedDate(java.time.LocalDate.now());
                    newModel.setEpochs(0);
                    newModel.setLoss(0.0);
                    newModel.setValidationLoss(0.0);
                    return newModel;
                });

        target.setActive(true);
        autoencoderModelRepository.save(target);
        log.info("Активирована модель: {}", modelName);
        return true;
    }

    public List<TrainingSessionDTO> getTrainingSessions() {
        refreshTrainingSessionsFromPython();
        return TrainingSessionMapper.INSTANCE.toDTOList(trainingSessionRepository.findAllByOrderByStartedAtDesc());
    }

    /**
     * Возвращает сессии обучения только из БД, без обращения к Python.
     * Используется когда Python-сервис недоступен.
     */
    public List<TrainingSessionDTO> getTrainingSessionsFromDb() {
        return TrainingSessionMapper.INSTANCE.toDTOList(
                trainingSessionRepository.findAllByOrderByStartedAtDesc());
    }

    /**
     * Обновляет статус RUNNING-сессий на основе уже полученных данных Python (без повторных HTTP-вызовов).
     * Принимает уже загруженные trainingStatus и history, чтобы не дублировать вызовы к Python API.
     */
    @Transactional
    public List<TrainingSessionDTO> getTrainingSessionsWithData(Map<String, Object> trainingStatus,
                                                                 List<Map<String, Object>> history) {
        TrainingSession runningSession = trainingSessionRepository.findFirstByStatusOrderByStartedAtDesc("RUNNING");
        if (runningSession != null
                && !"running".equalsIgnoreCase(readString(trainingStatus.get("status")))
                && !history.isEmpty()) {
            Map<String, Object> latestResult = history.get(0);
            runningSession.setFinishedAt(readDateTime(latestResult.get("finishedAt")));
            runningSession.setStatus(String.valueOf(latestResult.getOrDefault("status", "error")).toUpperCase());
            runningSession.setMessage(readString(latestResult.get("message")));
            runningSession.setDatasetSize(readInteger(latestResult.get("datasetSize")));
            runningSession.setLoss(readDouble(latestResult.get("loss")));
            runningSession.setValidationLoss(readDouble(latestResult.get("validationLoss")));
            runningSession.setPsnr(readDouble(latestResult.get("psnr")));
            runningSession.setSsim(readDouble(latestResult.get("ssim")));
            runningSession.setModelName(readString(latestResult.get("modelName")));
            trainingSessionRepository.save(runningSession);
            if ("OK".equalsIgnoreCase(runningSession.getStatus())) {
                updateAutoencoderModel(latestResult);
            }
        }
        return TrainingSessionMapper.INSTANCE.toDTOList(
                trainingSessionRepository.findAllByOrderByStartedAtDesc());
    }

    @Transactional
    public Map<String, Object> startTraining(int epochs, int batchSize, double learningRate, int imageSize) {
        Map<String, Object> response = autoencoderClientService.trainModel(epochs, batchSize, learningRate, imageSize);
        String responseStatus = readString(response.get("status"));

        if (!"accepted".equalsIgnoreCase(responseStatus)) {
            return response;
        }

        Employee currentEmployee = employeeService.getAuthenticationEmployee();

        TrainingSession session = new TrainingSession();
        session.setStartedAt(readDateTime(response.get("startedAt")));
        session.setStatus("RUNNING");
        session.setEpochs(epochs);
        session.setBatchSize(batchSize);
        session.setLearningRate(learningRate);
        session.setImageSize(imageSize);
        session.setDatasetSize(readInteger(response.get("datasetSize")));
        session.setMessage(readString(response.get("message")));
        session.setTriggeredBy(currentEmployee);
        trainingSessionRepository.save(session);
        return response;
    }

    @Transactional
    public void refreshTrainingSessionsFromPython() {
        TrainingSession runningSession = trainingSessionRepository.findFirstByStatusOrderByStartedAtDesc("RUNNING");
        if (runningSession == null) {
            return;
        }

        Map<String, Object> pythonStatus = autoencoderClientService.getTrainingStatus();
        if ("running".equalsIgnoreCase(readString(pythonStatus.get("status")))) {
            return;
        }

        List<Map<String, Object>> history = autoencoderClientService.getTrainingHistory();
        if (history.isEmpty()) {
            return;
        }

        Map<String, Object> latestResult = history.get(0);
        runningSession.setFinishedAt(readDateTime(latestResult.get("finishedAt")));
        runningSession.setStatus(String.valueOf(latestResult.getOrDefault("status", "error")).toUpperCase());
        runningSession.setMessage(readString(latestResult.get("message")));
        runningSession.setDatasetSize(readInteger(latestResult.get("datasetSize")));
        runningSession.setLoss(readDouble(latestResult.get("loss")));
        runningSession.setValidationLoss(readDouble(latestResult.get("validationLoss")));
        runningSession.setPsnr(readDouble(latestResult.get("psnr")));
        runningSession.setSsim(readDouble(latestResult.get("ssim")));
        runningSession.setModelName(readString(latestResult.get("modelName")));
        trainingSessionRepository.save(runningSession);

        if ("OK".equalsIgnoreCase(runningSession.getStatus())) {
            updateAutoencoderModel(latestResult);
        }
    }

    private void updateAutoencoderModel(Map<String, Object> response) {
        String modelName = readString(response.get("modelName"));
        if (modelName == null || modelName.isBlank()) {
            return;
        }

        AutoencoderModel autoencoderModel = autoencoderModelRepository.findByModelName(modelName)
                .orElseGet(AutoencoderModel::new);

        autoencoderModel.setModelName(modelName);
        autoencoderModel.setDescription("Управляемая модель нейросетевого улучшения изображений");
        autoencoderModel.setTrainedDate(LocalDate.now());
        autoencoderModel.setEpochs(readInteger(response.get("epochs")));
        autoencoderModel.setLoss(readDouble(response.get("loss")));
        autoencoderModel.setValidationLoss(readDouble(response.get("validationLoss")));
        autoencoderModel.setPsnr(readDouble(response.get("psnr")));
        autoencoderModel.setSsim(readDouble(response.get("ssim")));
        autoencoderModel.setActive(true);
        autoencoderModelRepository.save(autoencoderModel);
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double readDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime readDateTime(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value), PYTHON_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить дату обучения из Python-сервиса: {}", value);
            return LocalDateTime.now();
        }
    }
}
