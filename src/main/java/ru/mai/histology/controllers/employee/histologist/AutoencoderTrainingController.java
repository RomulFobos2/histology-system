package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.service.employee.histologist.AutoencoderTrainingService;
import ru.mai.histology.service.general.PythonServiceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
public class AutoencoderTrainingController {

    private final AutoencoderTrainingService autoencoderTrainingService;
    private final PythonServiceManager pythonServiceManager;

    public AutoencoderTrainingController(AutoencoderTrainingService autoencoderTrainingService,
                                         PythonServiceManager pythonServiceManager) {
        this.autoencoderTrainingService = autoencoderTrainingService;
        this.pythonServiceManager = pythonServiceManager;
    }

    @GetMapping("/employee/histologist/autoencoder/dashboard")
    public String dashboard(Model model) {
        log.debug("Загрузка дашборда автоэнкодера...");
        long t0 = System.currentTimeMillis();

        boolean serviceAvailable = autoencoderTrainingService.isServiceAvailable();
        model.addAttribute("serviceAvailable", serviceAvailable);
        model.addAttribute("processManaged", pythonServiceManager.isRunning());

        if (!serviceAvailable) {
            log.info("Python-сервис недоступен, данные дашборда из БД ({}ms)", System.currentTimeMillis() - t0);
            model.addAttribute("metrics", Map.of());
            model.addAttribute("trainingStatus",
                    Map.of("status", "offline", "message", "Python-сервис недоступен"));
            model.addAttribute("trainingSessions", autoencoderTrainingService.getTrainingSessionsFromDb());
            return "employee/histologist/autoencoder/dashboard";
        }

        Map<String, Object> trainingStatus = autoencoderTrainingService.getTrainingStatus();
        List<Map<String, Object>> pythonHistory = autoencoderTrainingService.getPythonTrainingHistory();
        model.addAttribute("metrics", autoencoderTrainingService.getMetrics());
        model.addAttribute("trainingStatus", trainingStatus);
        model.addAttribute("trainingSessions",
                autoencoderTrainingService.getTrainingSessionsWithData(trainingStatus, pythonHistory));

        log.debug("Дашборд загружен за {}ms", System.currentTimeMillis() - t0);
        return "employee/histologist/autoencoder/dashboard";
    }

    @GetMapping("/employee/histologist/autoencoder/api/training-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> trainingStatusApi() {
        Map<String, Object> result = new HashMap<>();
        boolean serviceAvailable = autoencoderTrainingService.isServiceAvailable();
        result.put("serviceAvailable", serviceAvailable);
        result.put("processManaged", pythonServiceManager.isRunning());
        if (serviceAvailable) {
            result.put("trainingStatus", autoencoderTrainingService.getTrainingStatus());
        } else {
            result.put("trainingStatus", Map.of("status", "offline"));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/employee/histologist/autoencoder/startService")
    public String startService(RedirectAttributes redirectAttributes) {
        boolean started = pythonServiceManager.start();
        if (started) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Python-сервис запускается. Подождите несколько секунд и обновите страницу.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось запустить Python-сервис. Возможно, он уже работает или не найден исполняемый файл Python.");
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }

    @PostMapping("/employee/histologist/autoencoder/stopService")
    public String stopService(RedirectAttributes redirectAttributes) {
        boolean stopped = pythonServiceManager.stop();
        if (stopped) {
            redirectAttributes.addFlashAttribute("successMessage", "Python-сервис остановлен.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось остановить сервис через систему — он был запущен вручную. Остановите uvicorn самостоятельно.");
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }

    @PostMapping("/employee/histologist/autoencoder/resetTrainingStatus")
    public String resetTrainingStatus(RedirectAttributes redirectAttributes) {
        Map<String, Object> result = autoencoderTrainingService.resetTrainingStatus();
        String status = String.valueOf(result.getOrDefault("status", "error"));
        if ("idle".equals(status)) {
            redirectAttributes.addFlashAttribute("successMessage", "Статус обучения сброшен.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось сбросить статус. Python-сервис недоступен или вернул ошибку.");
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }

    @PostMapping("/employee/histologist/autoencoder/activateModel")
    public String activateModel(@RequestParam String modelName, RedirectAttributes redirectAttributes) {
        boolean ok = autoencoderTrainingService.activateModel(modelName);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Модель «" + modelName + "» активирована для улучшения изображений.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось активировать модель.");
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }

    @PostMapping("/employee/histologist/autoencoder/clearSystemHistory")
    public String clearSystemHistory(RedirectAttributes redirectAttributes) {
        autoencoderTrainingService.clearSystemTrainingSessions();
        redirectAttributes.addFlashAttribute("successMessage", "История запусков в системе очищена.");
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }

    @PostMapping("/employee/histologist/autoencoder/clearPythonHistory")
    public String clearPythonHistory(RedirectAttributes redirectAttributes) {
        Map<String, Object> result = autoencoderTrainingService.clearPythonTrainingHistory();
        String status = String.valueOf(result.getOrDefault("status", "error"));
        if ("ok".equals(status)) {
            redirectAttributes.addFlashAttribute("successMessage", "История Python-сервиса очищена.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось очистить историю Python-сервиса.");
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }

    @PostMapping("/employee/histologist/autoencoder/train")
    public String train(@RequestParam int inputEpochs,
                        @RequestParam int inputBatchSize,
                        @RequestParam double inputLearningRate,
                        @RequestParam int inputImageSize,
                        RedirectAttributes redirectAttributes) {
        Map<String, Object> result = autoencoderTrainingService.startTraining(
                inputEpochs,
                inputBatchSize,
                inputLearningRate,
                inputImageSize
        );

        String status = String.valueOf(result.getOrDefault("status", "error"));
        String message = String.valueOf(result.getOrDefault("message", "Не удалось запустить обучение."));

        if ("accepted".equalsIgnoreCase(status)) {
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Обучение запущено в фоновом режиме. Страница будет обновлять статус автоматически."
            );
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", message);
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }
}
