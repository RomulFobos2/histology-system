package ru.mai.histology.controllers.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.service.employee.laborant.ImageUploadService;
import ru.mai.histology.service.employee.laborant.SampleService;

import java.util.Optional;

@Controller("laborantImageController")
@Slf4j
public class ImageController {

    private final ImageUploadService imageUploadService;
    private final SampleService sampleService;

    public ImageController(ImageUploadService imageUploadService,
                           SampleService sampleService) {
        this.imageUploadService = imageUploadService;
        this.sampleService = sampleService;
    }

    // ========== Галерея изображений образца ==========

    @GetMapping("/employee/laborant/images/allImages/{sampleId}")
    public String allImages(@PathVariable(value = "sampleId") long sampleId, Model model) {
        Optional<SampleDTO> sampleOpt = sampleService.getSampleById(sampleId);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/laborant/samples/allSamples";
        }
        model.addAttribute("sampleDTO", sampleOpt.get());
        model.addAttribute("allImages", imageUploadService.getImagesBySample(sampleId));
        return "employee/laborant/images/allImages";
    }

    // ========== Загрузка ==========

    @GetMapping("/employee/laborant/images/uploadImage/{sampleId}")
    public String uploadImageForm(@PathVariable(value = "sampleId") long sampleId, Model model) {
        Optional<SampleDTO> sampleOpt = sampleService.getSampleById(sampleId);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/laborant/samples/allSamples";
        }
        model.addAttribute("sampleDTO", sampleOpt.get());
        return "employee/laborant/images/uploadImage";
    }

    @PostMapping("/employee/laborant/images/uploadImage/{sampleId}")
    public String uploadImage(@PathVariable(value = "sampleId") long sampleId,
                              @RequestParam("inputFile") MultipartFile file,
                              @RequestParam(required = false) String inputDescription,
                              @RequestParam(required = false) String inputMagnification,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        Optional<Long> result = imageUploadService.saveImage(sampleId, file,
                inputDescription, inputMagnification);

        if (result.isEmpty()) {
            model.addAttribute("imageError", "Ошибка при загрузке. Допустимые форматы: TIF, JPG. Макс. размер: 50 МБ.");
            Optional<SampleDTO> sampleOpt = sampleService.getSampleById(sampleId);
            sampleOpt.ifPresent(dto -> model.addAttribute("sampleDTO", dto));
            return "employee/laborant/images/uploadImage";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Изображение успешно загружено.");
        return "redirect:/employee/laborant/images/allImages/" + sampleId;
    }

    // ========== Карточка изображения ==========

    @GetMapping("/employee/laborant/images/detailsImage/{id}")
    public String detailsImage(@PathVariable(value = "id") long id, Model model) {
        Optional<MicroscopeImageDTO> imageOpt = imageUploadService.getImageById(id);
        if (imageOpt.isEmpty()) {
            return "redirect:/employee/laborant/samples/allSamples";
        }
        model.addAttribute("imageDTO", imageOpt.get());
        return "employee/laborant/images/detailsImage";
    }

    // ========== Полноэкранный просмотр ==========

    @GetMapping("/employee/laborant/images/viewImage/{id}")
    public String viewImage(@PathVariable(value = "id") long id, Model model) {
        Optional<MicroscopeImageDTO> imageOpt = imageUploadService.getImageById(id);
        if (imageOpt.isEmpty()) {
            return "redirect:/employee/laborant/samples/allSamples";
        }
        model.addAttribute("imageDTO", imageOpt.get());
        return "employee/laborant/images/viewImage";
    }

    // ========== Удаление ==========

    @GetMapping("/employee/laborant/images/deleteImage/{id}")
    public String deleteImage(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        Optional<MicroscopeImageDTO> imageOpt = imageUploadService.getImageById(id);
        Long sampleId = imageOpt.map(MicroscopeImageDTO::getSampleId).orElse(null);

        if (imageUploadService.deleteImage(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Изображение удалено.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении изображения.");
        }

        if (sampleId != null) {
            return "redirect:/employee/laborant/images/allImages/" + sampleId;
        }
        return "redirect:/employee/laborant/samples/allSamples";
    }
}
