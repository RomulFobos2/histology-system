package ru.mai.histology.controllers.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.service.employee.head.ForensicConclusionService;
import ru.mai.histology.service.employee.head.OversightService;
import ru.mai.histology.repo.HistologistConclusionRepository;
import ru.mai.histology.repo.ForensicConclusionRepository;

import java.util.Optional;

/** Контроллер надзора — read-only просмотр дел и образцов для начальника БСМЭ */
@Controller
@Slf4j
public class OversightController {

    private final OversightService oversightService;
    private final ForensicConclusionService forensicConclusionService;
    private final HistologistConclusionRepository histConclusionRepository;
    private final ForensicConclusionRepository forensicConclusionRepository;

    public OversightController(OversightService oversightService,
                               ForensicConclusionService forensicConclusionService,
                               HistologistConclusionRepository histConclusionRepository,
                               ForensicConclusionRepository forensicConclusionRepository) {
        this.oversightService = oversightService;
        this.forensicConclusionService = forensicConclusionService;
        this.histConclusionRepository = histConclusionRepository;
        this.forensicConclusionRepository = forensicConclusionRepository;
    }

    // ========== Дела ==========

    @GetMapping("/employee/head/oversight/allCases")
    public String allCases(Model model) {
        model.addAttribute("allCases", oversightService.getAllCases());
        model.addAttribute("caseStatuses", CaseStatus.values());
        return "employee/head/oversight/allCases";
    }

    @GetMapping("/employee/head/oversight/detailsCase/{id}")
    public String detailsCase(@PathVariable(value = "id") long id, Model model) {
        Optional<ForensicCaseDTO> caseOpt = oversightService.getCaseById(id);
        if (caseOpt.isEmpty()) {
            return "redirect:/employee/head/oversight/allCases";
        }
        model.addAttribute("caseDTO", caseOpt.get());
        model.addAttribute("caseSamples", oversightService.getSamplesByCase(id));
        return "employee/head/oversight/detailsCase";
    }

    // ========== Образцы ==========

    @GetMapping("/employee/head/oversight/allSamples")
    public String allSamples(Model model) {
        model.addAttribute("allSamples", oversightService.getAllSamples());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        return "employee/head/oversight/allSamples";
    }

    @GetMapping("/employee/head/oversight/detailsSample/{id}")
    public String detailsSample(@PathVariable(value = "id") long id, Model model) {
        Optional<SampleDTO> sampleOpt = oversightService.getSampleById(id);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/head/oversight/allSamples";
        }

        SampleDTO sampleDTO = sampleOpt.get();
        model.addAttribute("sampleDTO", sampleDTO);

        // Проверяем наличие заключений
        model.addAttribute("hasHistologistConclusion", histConclusionRepository.existsBySampleId(id));
        model.addAttribute("hasForensicConclusion", forensicConclusionRepository.existsBySampleId(id));

        // Передаём данные заключений
        histConclusionRepository.findBySampleId(id).ifPresent(hc -> {
            model.addAttribute("histConclusionDiagnosis", hc.getDiagnosis());
            model.addAttribute("histConclusionText", hc.getConclusionText());
        });

        forensicConclusionService.getConclusionBySampleId(id).ifPresent(fc -> {
            model.addAttribute("forensicConclusionId", fc.getId());
            model.addAttribute("forensicConclusionIsFinal", fc.isFinal());
        });

        return "employee/head/oversight/detailsSample";
    }
}
