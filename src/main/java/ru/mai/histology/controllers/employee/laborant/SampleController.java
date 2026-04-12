package ru.mai.histology.controllers.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.service.employee.laborant.ForensicCaseService;
import ru.mai.histology.service.employee.laborant.SampleService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class SampleController {

    private final SampleService sampleService;
    private final ForensicCaseService forensicCaseService;
    private final EmployeeRepository employeeRepository;

    public SampleController(SampleService sampleService,
                            ForensicCaseService forensicCaseService,
                            EmployeeRepository employeeRepository) {
        this.sampleService = sampleService;
        this.forensicCaseService = forensicCaseService;
        this.employeeRepository = employeeRepository;
    }

    // ========== AJAX проверка уникальности номера образца ==========

    @GetMapping("/employee/laborant/samples/check-sampleNumber")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkSampleNumber(@RequestParam Long caseId,
                                                                   @RequestParam String sampleNumber,
                                                                   @RequestParam(required = false) Long id) {
        boolean exists;
        if (id != null) {
            exists = sampleService.checkSampleNumberExcluding(caseId, sampleNumber, id);
        } else {
            exists = sampleService.checkSampleNumber(caseId, sampleNumber);
        }
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    // ========== Список всех образцов ==========

    @GetMapping("/employee/laborant/samples/allSamples")
    public String allSamples(Model model) {
        model.addAttribute("allSamples", sampleService.getAllSamples());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        return "employee/laborant/samples/allSamples";
    }

    // ========== Добавление образца ==========

    @GetMapping("/employee/laborant/samples/addSample/{caseId}")
    public String addSampleForm(@PathVariable(value = "caseId") long caseId, Model model) {
        Optional<ForensicCaseDTO> caseOpt = forensicCaseService.getCaseById(caseId);
        if (caseOpt.isEmpty()) {
            return "redirect:/employee/laborant/cases/allCases";
        }
        model.addAttribute("caseDTO", caseOpt.get());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        model.addAttribute("allHistologists", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
        return "employee/laborant/samples/addSample";
    }

    @PostMapping("/employee/laborant/samples/addSample/{caseId}")
    public String addSample(@PathVariable(value = "caseId") long caseId,
                            @RequestParam String inputSampleNumber,
                            @RequestParam TissueType inputTissueType,
                            @RequestParam StainingMethod inputStainingMethod,
                            @RequestParam(required = false) Long inputHistologistId,
                            @RequestParam(required = false) String inputNotes,
                            Model model) {
        Optional<Long> result = sampleService.saveSample(caseId, inputSampleNumber, inputTissueType,
                inputStainingMethod, inputHistologistId, inputNotes);

        if (result.isEmpty()) {
            model.addAttribute("sampleError", "Ошибка при сохранении. Возможно, номер образца уже занят.");
            Optional<ForensicCaseDTO> caseOpt = forensicCaseService.getCaseById(caseId);
            caseOpt.ifPresent(dto -> model.addAttribute("caseDTO", dto));
            model.addAttribute("tissueTypes", TissueType.values());
            model.addAttribute("stainingMethods", StainingMethod.values());
            model.addAttribute("allHistologists", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
            return "employee/laborant/samples/addSample";
        }
        return "redirect:/employee/laborant/cases/detailsCase/" + caseId;
    }

    // ========== Просмотр образца ==========

    @GetMapping("/employee/laborant/samples/detailsSample/{id}")
    public String detailsSample(@PathVariable(value = "id") long id, Model model) {
        Optional<SampleDTO> sampleOpt = sampleService.getSampleById(id);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/laborant/samples/allSamples";
        }
        model.addAttribute("sampleDTO", sampleOpt.get());
        return "employee/laborant/samples/detailsSample";
    }

    // ========== Редактирование образца ==========

    @GetMapping("/employee/laborant/samples/editSample/{id}")
    public String editSampleForm(@PathVariable(value = "id") long id, Model model) {
        Optional<SampleDTO> sampleOpt = sampleService.getSampleById(id);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/laborant/samples/allSamples";
        }
        model.addAttribute("sampleDTO", sampleOpt.get());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        model.addAttribute("allHistologists", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
        return "employee/laborant/samples/editSample";
    }

    @PostMapping("/employee/laborant/samples/editSample/{id}")
    public String editSample(@PathVariable(value = "id") long id,
                             @RequestParam String inputSampleNumber,
                             @RequestParam TissueType inputTissueType,
                             @RequestParam StainingMethod inputStainingMethod,
                             @RequestParam(required = false) Long inputHistologistId,
                             @RequestParam(required = false) String inputNotes,
                             RedirectAttributes redirectAttributes) {
        Optional<Long> result = sampleService.editSample(id, inputSampleNumber, inputTissueType,
                inputStainingMethod, inputHistologistId, inputNotes);

        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("sampleError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/laborant/samples/editSample/" + id;
        }
        return "redirect:/employee/laborant/samples/detailsSample/" + id;
    }

    // ========== Удаление образца ==========

    @GetMapping("/employee/laborant/samples/deleteSample/{id}")
    public String deleteSample(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        Optional<SampleDTO> sampleOpt = sampleService.getSampleById(id);
        Long caseId = sampleOpt.map(SampleDTO::getCaseId).orElse(null);

        if (sampleService.deleteSample(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Образец успешно удалён.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении образца.");
        }

        if (caseId != null) {
            return "redirect:/employee/laborant/cases/detailsCase/" + caseId;
        }
        return "redirect:/employee/laborant/samples/allSamples";
    }

}
