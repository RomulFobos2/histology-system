package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.EnhancementQuality;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.service.employee.histologist.ImageEnhancementService;
import ru.mai.histology.service.employee.histologist.ImageViewService;
import ru.mai.histology.service.employee.histologist.SampleViewService;
import ru.mai.histology.service.employee.laborant.SampleService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class HistologistImageController {

    private final ImageViewService imageViewService;
    private final ImageEnhancementService imageEnhancementService;
    private final SampleService sampleService;
    private final SampleViewService sampleViewService;

    public HistologistImageController(ImageViewService imageViewService,
                                      ImageEnhancementService imageEnhancementService,
                                      SampleService sampleService,
                                      SampleViewService sampleViewService) {
        this.imageViewService = imageViewService;
        this.imageEnhancementService = imageEnhancementService;
        this.sampleService = sampleService;
        this.sampleViewService = sampleViewService;
    }

    /** Назначен ли образец, к которому привязано изображение, текущему гистологу. */
    private boolean isImageAccessible(Long imageId) {
        return imageViewService.getImageById(imageId)
                .map(img -> sampleViewService.isAssignedToCurrentUser(img.getSampleId()))
                .orElse(false);
    }

    // ========== Галерея всех изображений в системе ==========

    @GetMapping("/employee/histologist/images/all")
    public String allImagesGlobal(Model model) {
        model.addAttribute("allImages", imageViewService.getAllImages());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        return "employee/histologist/images/allImagesGlobal";
    }

    // ========== Галерея изображений ==========

    @GetMapping("/employee/histologist/images/allImages/{sampleId}")
    public String allImages(@PathVariable(value = "sampleId") long sampleId, Model model) {
        if (!sampleViewService.isAssignedToCurrentUser(sampleId)) {
            return "redirect:/employee/histologist/samples/allSamples";
        }
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
        if (!isImageAccessible(id)) {
            return "redirect:/employee/histologist/images/all";
        }
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
        if (!isImageAccessible(id)) {
            return "redirect:/employee/histologist/images/all";
        }
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
        if (!isImageAccessible(id)) {
            return "redirect:/employee/histologist/images/all";
        }
        Optional<Long> enhancedImageId = imageEnhancementService.enhanceImage(id, mode);
        if (enhancedImageId.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось улучшить изображение. Проверьте, что Python-сервис автоэнкодера запущен.");
            return "redirect:/employee/histologist/images/detailsImage/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Улучшенная копия изображения успешно создана.");
        redirectAttributes.addFlashAttribute("showRatingModal", true);
        return "redirect:/employee/histologist/images/detailsImage/" + enhancedImageId.get();
    }

    // ========== Оценка качества улучшения ==========

    @PostMapping("/employee/histologist/images/rate/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateImage(@PathVariable("id") long id,
                                                         @RequestParam("quality") EnhancementQuality quality) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (!isImageAccessible(id)) {
            response.put("ok", false);
            response.put("error", "Доступ к изображению запрещён.");
            return ResponseEntity.status(403).body(response);
        }
        boolean ok = imageEnhancementService.rateEnhancement(id, quality);
        if (!ok) {
            response.put("ok", false);
            response.put("error", "Не удалось сохранить оценку. Изображение должно быть улучшенным.");
            return ResponseEntity.badRequest().body(response);
        }
        response.put("ok", true);
        response.put("quality", quality.name());
        response.put("displayName", quality.getDisplayName());
        response.put("cssClass", switch (quality) {
            case BAD -> "bg-danger";
            case GOOD -> "bg-warning text-dark";
            case EXCELLENT -> "bg-success";
        });
        return ResponseEntity.ok(response);
    }

    // ========== Сравнение ==========

    @GetMapping("/employee/histologist/images/compare/{originalId}/{enhancedId}")
    public String compareImages(@PathVariable(value = "originalId") long originalId,
                                @PathVariable(value = "enhancedId") long enhancedId,
                                Model model) {
        if (!isImageAccessible(originalId) || !isImageAccessible(enhancedId)) {
            return "redirect:/employee/histologist/images/all";
        }
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
