package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.service.employee.histologist.ImageEnhancementService;
import ru.mai.histology.service.employee.histologist.ImageViewService;
import ru.mai.histology.service.employee.laborant.SampleService;

import java.util.Optional;

@Controller
@Slf4j
public class HistologistImageController {

    private final ImageViewService imageViewService;
    private final ImageEnhancementService imageEnhancementService;
    private final SampleService sampleService;

    public HistologistImageController(ImageViewService imageViewService,
                                      ImageEnhancementService imageEnhancementService,
                                      SampleService sampleService) {
        this.imageViewService = imageViewService;
        this.imageEnhancementService = imageEnhancementService;
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
        MicroscopeImageDTO imageDTO = imageOpt.get();
        model.addAttribute("imageDTO", imageDTO);

        if (imageDTO.getOriginalImageId() != null) {
            model.addAttribute("compareOriginalId", imageDTO.getOriginalImageId());
            model.addAttribute("compareEnhancedId", imageDTO.getId());
        } else {
            imageViewService.getLatestEnhancedVersion(imageDTO.getId()).ifPresent(enhancedDTO -> {
                model.addAttribute("compareOriginalId", imageDTO.getId());
                model.addAttribute("compareEnhancedId", enhancedDTO.getId());
            });
        }

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

    // ========== Улучшение ==========

    @PostMapping("/employee/histologist/images/enhance/{id}")
    public String enhanceImage(@PathVariable(value = "id") long id,
                               @RequestParam(value = "mode", defaultValue = "auto") String mode,
                               RedirectAttributes redirectAttributes) {
        Optional<Long> enhancedImageId = imageEnhancementService.enhanceImage(id, mode);
        if (enhancedImageId.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось улучшить изображение. Проверьте, что Python-сервис автоэнкодера запущен.");
            return "redirect:/employee/histologist/images/detailsImage/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Улучшенная копия изображения успешно создана.");
        return "redirect:/employee/histologist/images/detailsImage/" + enhancedImageId.get();
    }

    // ========== Сравнение ==========

    @GetMapping("/employee/histologist/images/compare/{originalId}/{enhancedId}")
    public String compareImages(@PathVariable(value = "originalId") long originalId,
                                @PathVariable(value = "enhancedId") long enhancedId,
                                Model model) {
        Optional<MicroscopeImageDTO> originalOpt = imageViewService.getImageById(originalId);
        Optional<MicroscopeImageDTO> enhancedOpt = imageViewService.getImageById(enhancedId);

        if (originalOpt.isEmpty() || enhancedOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }

        model.addAttribute("originalImageDTO", originalOpt.get());
        model.addAttribute("enhancedImageDTO", enhancedOpt.get());
        return "employee/histologist/images/compare";
    }
}
