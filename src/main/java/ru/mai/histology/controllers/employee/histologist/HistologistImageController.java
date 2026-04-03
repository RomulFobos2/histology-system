package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.service.employee.histologist.ImageViewService;
import ru.mai.histology.service.employee.laborant.SampleService;

import java.util.Optional;

@Controller
@Slf4j
public class HistologistImageController {

    private final ImageViewService imageViewService;
    private final SampleService sampleService;

    public HistologistImageController(ImageViewService imageViewService,
                                      SampleService sampleService) {
        this.imageViewService = imageViewService;
        this.sampleService = sampleService;
    }

    // ========== Галерея изображений ==========

    @GetMapping("/employee/histologist/images/allImages/{sampleId}")
    public String allImages(@PathVariable(value = "sampleId") long sampleId, Model model) {
        Optional<SampleDTO> sampleOpt = sampleService.getSampleById(sampleId);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }
        model.addAttribute("sampleDTO", sampleOpt.get());
        model.addAttribute("allImages", imageViewService.getImagesBySample(sampleId));
        return "employee/histologist/images/allImages";
    }

    // ========== Карточка изображения ==========

    @GetMapping("/employee/histologist/images/detailsImage/{id}")
    public String detailsImage(@PathVariable(value = "id") long id, Model model) {
        Optional<MicroscopeImageDTO> imageOpt = imageViewService.getImageById(id);
        if (imageOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }
        model.addAttribute("imageDTO", imageOpt.get());
        return "employee/histologist/images/detailsImage";
    }

    // ========== Полноэкранный просмотр ==========

    @GetMapping("/employee/histologist/images/viewImage/{id}")
    public String viewImage(@PathVariable(value = "id") long id, Model model) {
        Optional<MicroscopeImageDTO> imageOpt = imageViewService.getImageById(id);
        if (imageOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }
        model.addAttribute("imageDTO", imageOpt.get());
        return "employee/histologist/images/viewImage";
    }

    // ========== Улучшение (заглушка для этапа 6) ==========

    @PostMapping("/employee/histologist/images/enhance/{id}")
    public String enhanceImage(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Функция улучшения изображений будет доступна позже.");
        return "redirect:/employee/histologist/images/detailsImage/" + id;
    }

    // ========== Сравнение (заглушка для этапа 6) ==========

    @GetMapping("/employee/histologist/images/compare/{originalId}/{enhancedId}")
    public String compareImages(@PathVariable long originalId, @PathVariable long enhancedId, Model model) {
        model.addAttribute("message", "Функция сравнения изображений будет доступна позже.");
        return "employee/histologist/images/detailsImage";
    }
}
