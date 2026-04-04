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
import java.util.List;
import java.util.Map;

@Service
@Getter
@Slf4j
public class AutoencoderTrainingService {

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

    public List<TrainingSessionDTO> getTrainingSessions() {
        return TrainingSessionMapper.INSTANCE.toDTOList(trainingSessionRepository.findAllByOrderByStartedAtDesc());
    }

    @Transactional
    public boolean startTraining(int epochs, int batchSize, double learningRate, int imageSize) {
        Employee currentEmployee = employeeService.getAuthenticationEmployee();

        TrainingSession session = new TrainingSession();
        session.setStartedAt(LocalDateTime.now());
        session.setStatus("RUNNING");
        session.setEpochs(epochs);
        session.setBatchSize(batchSize);
        session.setLearningRate(learningRate);
        session.setImageSize(imageSize);
        session.setTriggeredBy(currentEmployee);
        trainingSessionRepository.save(session);

        try {
            Map<String, Object> response = autoencoderClientService.trainModel(epochs, batchSize, learningRate, imageSize);
            session.setFinishedAt(LocalDateTime.now());
            session.setStatus(String.valueOf(response.getOrDefault("status", "error")).toUpperCase());
            session.setMessage(String.valueOf(response.getOrDefault("message", "Нет ответа от Python-сервиса")));
            session.setDatasetSize(readInteger(response.get("datasetSize")));
            session.setLoss(readDouble(response.get("loss")));
            session.setValidationLoss(readDouble(response.get("validationLoss")));
            session.setPsnr(readDouble(response.get("psnr")));
            session.setSsim(readDouble(response.get("ssim")));
            session.setModelName(readString(response.get("modelName")));

            trainingSessionRepository.save(session);

            if ("ok".equalsIgnoreCase(readString(response.get("status")))) {
                updateAutoencoderModel(response);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Ошибка при запуске обучения автоэнкодера: {}", e.getMessage(), e);
            session.setFinishedAt(LocalDateTime.now());
            session.setStatus("ERROR");
            session.setMessage("Ошибка при запуске обучения: " + e.getMessage());
            trainingSessionRepository.save(session);
            return false;
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
}
