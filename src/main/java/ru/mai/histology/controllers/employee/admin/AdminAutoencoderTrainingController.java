package ru.mai.histology.controllers.employee.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.service.employee.head.ReportsService;
import ru.mai.histology.service.employee.histologist.AutoencoderTrainingService;
import ru.mai.histology.service.general.PythonServiceManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Зеркало AutoencoderTrainingController для роли ROLE_EMPLOYEE_ADMIN.
 * Делегирует во те же сервисы, чтобы не дублировать бизнес-логику обучения.
 * Отдельно дополняет дашборд статистикой по качеству улучшенных изображений
 * (BAD/GOOD/EXCELLENT по моделям) — администратор использует её, чтобы
 * принять решение о переобучении автоэнкодера.
 */
@Controller
@Slf4j
public class AdminAutoencoderTrainingController {

    private final AutoencoderTrainingService autoencoderTrainingService;
    private final PythonServiceManager pythonServiceManager;
    private final ReportsService reportsService;

    public AdminAutoencoderTrainingController(AutoencoderTrainingService autoencoderTrainingService,
                                              PythonServiceManager pythonServiceManager,
                                              ReportsService reportsService) {
        this.autoencoderTrainingService = autoencoderTrainingService;
        this.pythonServiceManager = pythonServiceManager;
        this.reportsService = reportsService;
    }

    @GetMapping("/employee/admin/autoencoder/dashboard")
    public String dashboard(Model model, HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String via = request.getHeader("X-Requested-With");
        log.info("[ADMIN-AE] GET /dashboard  Referer='{}' X-Requested-With='{}'", referer, via);
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
        } else {
            Map<String, Object> trainingStatus = autoencoderTrainingService.getTrainingStatus();
            List<Map<String, Object>> pythonHistory = autoencoderTrainingService.getPythonTrainingHistory();
            model.addAttribute("metrics", autoencoderTrainingService.getMetrics());
            model.addAttribute("trainingStatus", trainingStatus);
            model.addAttribute("trainingSessions",
                    autoencoderTrainingService.getTrainingSessionsWithData(trainingStatus, pythonHistory));
        }

        // Качество улучшенных изображений за всё время — для оценки необходимости переобучения
        Map<String, Object> qualityReport = reportsService.getImageProcessingReport(
                LocalDate.of(2000, 1, 1),
                LocalDate.now().plusDays(1)
        );
        model.addAttribute("qualityReport", qualityReport);

        log.debug("Дашборд загружен за {}ms", System.currentTimeMillis() - t0);
        return "employee/admin/autoencoder/dashboard";
    }

    @GetMapping("/employee/admin/autoencoder/api/training-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> trainingStatusApi() {
        Map<String, Object> result = new HashMap<>();
        boolean serviceAvailable = autoencoderTrainingService.isServiceAvailable();
        result.put("serviceAvailable", serviceAvailable);
        result.put("processManaged", pythonServiceManager.isRunning());
        if (serviceAvailable) {
            result.put("trainingStatus", autoencoderTrainingService.getTrainingStatus());
            result.put("metrics", autoencoderTrainingService.getMetrics());
        } else {
            result.put("trainingStatus", Map.of("status", "offline"));
            result.put("metrics", Map.of());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/employee/admin/autoencoder/startService")
    public String startService(RedirectAttributes redirectAttributes) {
        boolean started = pythonServiceManager.start();
        if (started) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Python-сервис запускается. Подождите несколько секунд и обновите страницу.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось запустить Python-сервис. Возможно, он уже работает или не найден исполняемый файл Python.");
        }
        return "redirect:/employee/admin/autoencoder/dashboard";
    }

    @PostMapping("/employee/admin/autoencoder/stopService")
    public String stopService(RedirectAttributes redirectAttributes) {
        boolean stopped = pythonServiceManager.stop();
        if (stopped) {
            redirectAttributes.addFlashAttribute("successMessage", "Python-сервис остановлен.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось остановить сервис через систему — он был запущен вручную. Остановите uvicorn самостоятельно.");
        }
        return "redirect:/employee/admin/autoencoder/dashboard";
    }

    @PostMapping("/employee/admin/autoencoder/resetTrainingStatus")
    public String resetTrainingStatus(RedirectAttributes redirectAttributes) {
        Map<String, Object> result = autoencoderTrainingService.resetTrainingStatus();
        String status = String.valueOf(result.getOrDefault("status", "error"));
        if ("idle".equals(status)) {
            redirectAttributes.addFlashAttribute("successMessage", "Статус обучения сброшен.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось сбросить статус. Python-сервис недоступен или вернул ошибку.");
        }
        return "redirect:/employee/admin/autoencoder/dashboard";
    }

    @PostMapping("/employee/admin/autoencoder/activateModel")
    public String activateModel(@RequestParam String modelName, RedirectAttributes redirectAttributes) {
        boolean ok = autoencoderTrainingService.activateModel(modelName);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Модель «" + modelName + "» активирована для улучшения изображений.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось активировать модель.");
        }
        return "redirect:/employee/admin/autoencoder/dashboard";
    }

    @PostMapping("/employee/admin/autoencoder/clearSystemHistory")
    public String clearSystemHistory(RedirectAttributes redirectAttributes) {
        autoencoderTrainingService.clearSystemTrainingSessions();
        redirectAttributes.addFlashAttribute("successMessage", "История запусков в системе очищена.");
        return "redirect:/employee/admin/autoencoder/dashboard";
    }

    @PostMapping("/employee/admin/autoencoder/clearPythonHistory")
    public String clearPythonHistory(RedirectAttributes redirectAttributes) {
        Map<String, Object> result = autoencoderTrainingService.clearPythonTrainingHistory();
        String status = String.valueOf(result.getOrDefault("status", "error"));
        if ("ok".equals(status)) {
            redirectAttributes.addFlashAttribute("successMessage", "История Python-сервиса очищена.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось очистить историю Python-сервиса.");
        }
        return "redirect:/employee/admin/autoencoder/dashboard";
    }

    @PostMapping("/employee/admin/autoencoder/train")
    public String train(@RequestParam int inputEpochs,
                        @RequestParam int inputBatchSize,
                        @RequestParam double inputLearningRate,
                        @RequestParam int inputImageSize,
                        RedirectAttributes redirectAttributes,
                        HttpServletRequest request) {
        log.info("[ADMIN-AE] POST /train epochs={} batchSize={} lr={} imageSize={} | Referer='{}' X-Requested-With='{}'",
                inputEpochs, inputBatchSize, inputLearningRate, inputImageSize,
                request.getHeader("Referer"), request.getHeader("X-Requested-With"));
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
        return "redirect:/employee/admin/autoencoder/dashboard";
    }
}
