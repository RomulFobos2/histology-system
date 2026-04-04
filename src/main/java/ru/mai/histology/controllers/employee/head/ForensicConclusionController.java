package ru.mai.histology.controllers.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.mai.histology.dto.ForensicConclusionDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.repo.HistologistConclusionRepository;
import ru.mai.histology.service.employee.head.ForensicConclusionService;
import ru.mai.histology.service.employee.head.OversightService;

import java.util.Optional;

/** Контроллер судебно-медицинских заключений начальника БСМЭ */
@Controller
@Slf4j
public class ForensicConclusionController {

    private final ForensicConclusionService conclusionService;
    private final OversightService oversightService;
    private final HistologistConclusionRepository histConclusionRepository;

    public ForensicConclusionController(ForensicConclusionService conclusionService,
                                        OversightService oversightService,
                                        HistologistConclusionRepository histConclusionRepository) {
        this.conclusionService = conclusionService;
        this.oversightService = oversightService;
        this.histConclusionRepository = histConclusionRepository;
    }

    // ========== Список заключений ==========

    @GetMapping("/employee/head/conclusions/allConclusions")
    public String allConclusions(Model model) {
        model.addAttribute("allConclusions", conclusionService.getAllConclusions());
        return "employee/head/conclusions/allConclusions";
    }

    // ========== Создание заключения ==========

    @GetMapping("/employee/head/conclusions/addConclusion/{sampleId}")
    public String addConclusionForm(@PathVariable(value = "sampleId") long sampleId, Model model) {
        Optional<SampleDTO> sampleOpt = oversightService.getSampleById(sampleId);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/head/oversight/allSamples";
        }

        if (conclusionService.conclusionExistsForSample(sampleId)) {
            return "redirect:/employee/head/oversight/detailsSample/" + sampleId;
        }

        model.addAttribute("sampleDTO", sampleOpt.get());

        // Показываем заключение гистолога для справки
        histConclusionRepository.findBySampleId(sampleId).ifPresent(hc -> {
            model.addAttribute("histDiagnosis", hc.getDiagnosis());
            model.addAttribute("histConclusionText", hc.getConclusionText());
            model.addAttribute("histMicroscopicDescription", hc.getMicroscopicDescription());
        });

        return "employee/head/conclusions/addConclusion";
    }

    @PostMapping("/employee/head/conclusions/addConclusion/{sampleId}")
    public String addConclusion(@PathVariable(value = "sampleId") long sampleId,
                                @RequestParam String inputConclusionText,
                                @RequestParam(required = false) String inputIsFinal,
                                Model model) {
        boolean isFinal = "on".equals(inputIsFinal);

        Optional<Long> savedId = conclusionService.saveConclusion(sampleId, inputConclusionText, isFinal);

        if (savedId.isEmpty()) {
            model.addAttribute("conclusionError", "Ошибка при сохранении заключения. Проверьте введённые данные.");
            oversightService.getSampleById(sampleId).ifPresent(s -> model.addAttribute("sampleDTO", s));
            histConclusionRepository.findBySampleId(sampleId).ifPresent(hc -> {
                model.addAttribute("histDiagnosis", hc.getDiagnosis());
                model.addAttribute("histConclusionText", hc.getConclusionText());
                model.addAttribute("histMicroscopicDescription", hc.getMicroscopicDescription());
            });
            return "employee/head/conclusions/addConclusion";
        }

        return "redirect:/employee/head/conclusions/detailsConclusion/" + savedId.get();
    }

    // ========== Карточка заключения ==========

    @GetMapping("/employee/head/conclusions/detailsConclusion/{id}")
    public String detailsConclusion(@PathVariable(value = "id") long id, Model model) {
        Optional<ForensicConclusionDTO> conclusionOpt = conclusionService.getConclusionById(id);
        if (conclusionOpt.isEmpty()) {
            return "redirect:/employee/head/conclusions/allConclusions";
        }
        model.addAttribute("conclusionDTO", conclusionOpt.get());
        return "employee/head/conclusions/detailsConclusion";
    }

    // ========== Редактирование ==========

    @GetMapping("/employee/head/conclusions/editConclusion/{id}")
    public String editConclusionForm(@PathVariable(value = "id") long id, Model model) {
        Optional<ForensicConclusionDTO> conclusionOpt = conclusionService.getConclusionById(id);
        if (conclusionOpt.isEmpty()) {
            return "redirect:/employee/head/conclusions/allConclusions";
        }
        model.addAttribute("conclusionDTO", conclusionOpt.get());
        return "employee/head/conclusions/editConclusion";
    }

    @PostMapping("/employee/head/conclusions/editConclusion/{id}")
    public String editConclusion(@PathVariable(value = "id") long id,
                                 @RequestParam String inputConclusionText,
                                 @RequestParam(required = false) String inputIsFinal,
                                 Model model) {
        boolean isFinal = "on".equals(inputIsFinal);

        Optional<Long> editedId = conclusionService.editConclusion(id, inputConclusionText, isFinal);

        if (editedId.isEmpty()) {
            model.addAttribute("conclusionError", "Ошибка при обновлении заключения.");
            conclusionService.getConclusionById(id).ifPresent(c -> model.addAttribute("conclusionDTO", c));
            return "employee/head/conclusions/editConclusion";
        }

        return "redirect:/employee/head/conclusions/detailsConclusion/" + id;
    }
}
