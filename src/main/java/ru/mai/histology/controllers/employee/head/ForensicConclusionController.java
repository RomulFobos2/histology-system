package ru.mai.histology.controllers.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.mai.histology.dto.ForensicConclusionDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.repo.HistologistConclusionRepository;
import ru.mai.histology.service.employee.head.ForensicConclusionService;
import ru.mai.histology.service.employee.head.OversightService;
import ru.mai.histology.service.general.WordExportService;

import java.util.Optional;

/** Контроллер судебно-медицинских заключений начальника БСМЭ */
@Controller
@Slf4j
public class ForensicConclusionController {

    private final ForensicConclusionService conclusionService;
    private final OversightService oversightService;
    private final HistologistConclusionRepository histConclusionRepository;
    private final WordExportService wordExportService;

    public ForensicConclusionController(ForensicConclusionService conclusionService,
                                        OversightService oversightService,
                                        HistologistConclusionRepository histConclusionRepository,
                                        WordExportService wordExportService) {
        this.conclusionService = conclusionService;
        this.oversightService = oversightService;
        this.histConclusionRepository = histConclusionRepository;
        this.wordExportService = wordExportService;
    }

    // ========== Список заключений ==========

    @GetMapping("/employee/head/conclusions/allConclusions")
    public String allConclusions(Model model) {
        model.addAttribute("allConclusions", conclusionService.getAllConclusions());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        model.addAttribute("sampleStatuses", SampleStatus.values());
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

    // ========== Экспорт в Word ==========

    @PostMapping("/employee/head/conclusions/exportConclusion/{id}")
    public ResponseEntity<byte[]> exportConclusion(@PathVariable(value = "id") long id) {
        Optional<ForensicConclusionDTO> conclusionOpt = conclusionService.getConclusionById(id);
        if (conclusionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] docx = wordExportService.exportConclusion(conclusionOpt.get());
            String filename = "conclusion_" + id + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docx);
        } catch (Exception e) {
            log.error("Ошибка экспорта заключения id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
