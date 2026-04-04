package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.service.employee.histologist.AutoencoderTrainingService;

import java.util.Map;

@Controller
@Slf4j
public class AutoencoderTrainingController {

    private final AutoencoderTrainingService autoencoderTrainingService;

    public AutoencoderTrainingController(AutoencoderTrainingService autoencoderTrainingService) {
        this.autoencoderTrainingService = autoencoderTrainingService;
    }

    @GetMapping("/employee/histologist/autoencoder/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("serviceAvailable", autoencoderTrainingService.isServiceAvailable());
        model.addAttribute("metrics", autoencoderTrainingService.getMetrics());
        model.addAttribute("trainingStatus", autoencoderTrainingService.getTrainingStatus());
        model.addAttribute("models", autoencoderTrainingService.getModels());
        model.addAttribute("pythonTrainingHistory", autoencoderTrainingService.getPythonTrainingHistory());
        model.addAttribute("trainingSessions", autoencoderTrainingService.getTrainingSessions());
        return "employee/histologist/autoencoder/dashboard";
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
