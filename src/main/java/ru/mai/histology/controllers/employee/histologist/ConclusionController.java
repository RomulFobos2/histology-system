package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.HistologistConclusionDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.service.employee.histologist.ConclusionService;
import ru.mai.histology.service.employee.histologist.SampleViewService;

import java.util.Optional;

/** Контроллер заключений врача-гистолога */
@Controller
@Slf4j
public class ConclusionController {

    private final ConclusionService conclusionService;
    private final SampleViewService sampleViewService;

    public ConclusionController(ConclusionService conclusionService,
                                SampleViewService sampleViewService) {
        this.conclusionService = conclusionService;
        this.sampleViewService = sampleViewService;
    }

    // ========== Список заключений ==========

    @GetMapping("/employee/histologist/conclusions/allConclusions")
    public String allConclusions(Model model) {
        model.addAttribute("allConclusions", conclusionService.getAllConclusions());
        return "employee/histologist/conclusions/allConclusions";
    }

    // ========== Создание заключения ==========

    @GetMapping("/employee/histologist/conclusions/addConclusion/{sampleId}")
    public String addConclusionForm(@PathVariable(value = "sampleId") long sampleId, Model model) {
        Optional<SampleDTO> sampleOpt = sampleViewService.getSampleById(sampleId);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }

        // Проверяем, нет ли уже заключения для этого образца
        if (conclusionService.conclusionExistsForSample(sampleId)) {
            return "redirect:/employee/histologist/samples/detailsSample/" + sampleId;
        }

        model.addAttribute("sampleDTO", sampleOpt.get());
        return "employee/histologist/conclusions/addConclusion";
    }

    @PostMapping("/employee/histologist/conclusions/addConclusion/{sampleId}")
    public String addConclusion(@PathVariable(value = "sampleId") long sampleId,
                                @RequestParam String inputMicroscopicDescription,
                                @RequestParam String inputDiagnosis,
                                @RequestParam String inputConclusionText,
                                Model model) {
        Optional<Long> savedId = conclusionService.saveConclusion(sampleId,
                inputMicroscopicDescription, inputDiagnosis, inputConclusionText);

        if (savedId.isEmpty()) {
            model.addAttribute("conclusionError", "Ошибка при сохранении заключения. Проверьте введённые данные.");
            sampleViewService.getSampleById(sampleId).ifPresent(s -> model.addAttribute("sampleDTO", s));
            return "employee/histologist/conclusions/addConclusion";
        }

        return "redirect:/employee/histologist/conclusions/detailsConclusion/" + savedId.get();
    }

    // ========== Карточка заключения ==========

    @GetMapping("/employee/histologist/conclusions/detailsConclusion/{id}")
    public String detailsConclusion(@PathVariable(value = "id") long id, Model model) {
        Optional<HistologistConclusionDTO> conclusionOpt = conclusionService.getConclusionById(id);
        if (conclusionOpt.isEmpty()) {
            return "redirect:/employee/histologist/conclusions/allConclusions";
        }
        model.addAttribute("conclusionDTO", conclusionOpt.get());
        return "employee/histologist/conclusions/detailsConclusion";
    }

    // ========== Редактирование ==========

    @GetMapping("/employee/histologist/conclusions/editConclusion/{id}")
    public String editConclusionForm(@PathVariable(value = "id") long id, Model model) {
        Optional<HistologistConclusionDTO> conclusionOpt = conclusionService.getConclusionById(id);
        if (conclusionOpt.isEmpty()) {
            return "redirect:/employee/histologist/conclusions/allConclusions";
        }
        model.addAttribute("conclusionDTO", conclusionOpt.get());
        return "employee/histologist/conclusions/editConclusion";
    }

    @PostMapping("/employee/histologist/conclusions/editConclusion/{id}")
    public String editConclusion(@PathVariable(value = "id") long id,
                                 @RequestParam String inputMicroscopicDescription,
                                 @RequestParam String inputDiagnosis,
                                 @RequestParam String inputConclusionText,
                                 Model model) {
        Optional<Long> editedId = conclusionService.editConclusion(id,
                inputMicroscopicDescription, inputDiagnosis, inputConclusionText);

        if (editedId.isEmpty()) {
            model.addAttribute("conclusionError", "Ошибка при обновлении заключения.");
            conclusionService.getConclusionById(id).ifPresent(c -> model.addAttribute("conclusionDTO", c));
            return "employee/histologist/conclusions/editConclusion";
        }

        return "redirect:/employee/histologist/conclusions/detailsConclusion/" + id;
    }

    // ========== Удаление ==========

    @GetMapping("/employee/histologist/conclusions/deleteConclusion/{id}")
    public String deleteConclusion(@PathVariable(value = "id") long id, RedirectAttributes attrs) {
        if (!conclusionService.deleteConclusion(id)) {
            attrs.addFlashAttribute("errorMessage", "Не удалось удалить заключение.");
            return "redirect:/employee/histologist/conclusions/detailsConclusion/" + id;
        }
        return "redirect:/employee/histologist/conclusions/allConclusions";
    }
}
