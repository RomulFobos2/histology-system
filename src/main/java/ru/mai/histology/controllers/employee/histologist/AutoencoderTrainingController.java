package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.service.employee.histologist.AutoencoderTrainingService;

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

    @PostMapping("/employee/histologist/autoencoder/train")
    public String train(@RequestParam int inputEpochs,
                        @RequestParam int inputBatchSize,
                        @RequestParam double inputLearningRate,
                        @RequestParam int inputImageSize,
                        RedirectAttributes redirectAttributes) {
        boolean success = autoencoderTrainingService.startTraining(
                inputEpochs,
                inputBatchSize,
                inputLearningRate,
                inputImageSize
        );

        if (success) {
            redirectAttributes.addFlashAttribute("successMessage", "Обучение модели успешно завершено.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Обучение модели завершилось с ошибкой. Проверьте статус и историю запусков.");
        }
        return "redirect:/employee/histologist/autoencoder/dashboard";
    }
}
