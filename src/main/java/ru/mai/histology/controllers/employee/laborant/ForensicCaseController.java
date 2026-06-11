package ru.mai.histology.controllers.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.service.employee.laborant.ForensicCaseService;
import ru.mai.histology.service.employee.laborant.SampleService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class ForensicCaseController {

    private final ForensicCaseService forensicCaseService;
    private final SampleService sampleService;
    private final EmployeeRepository employeeRepository;

    public ForensicCaseController(ForensicCaseService forensicCaseService,
                                  SampleService sampleService,
                                  EmployeeRepository employeeRepository) {
        this.forensicCaseService = forensicCaseService;
        this.sampleService = sampleService;
        this.employeeRepository = employeeRepository;
    }

    // ========== AJAX проверка уникальности номера дела ==========

    @GetMapping("/employee/laborant/cases/check-caseNumber")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkCaseNumber(@RequestParam String caseNumber,
                                                                 @RequestParam(required = false) Long id) {
        boolean exists;
        if (id != null) {
            exists = forensicCaseService.checkCaseNumberExcluding(caseNumber, id);
        } else {
            exists = forensicCaseService.checkCaseNumber(caseNumber);
        }
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    // ========== Список дел ==========

    @GetMapping("/employee/laborant/cases/allCases")
    public String allCases(Model model) {
        model.addAttribute("allCases", forensicCaseService.getAllCases());
        model.addAttribute("caseStatuses", CaseStatus.values());
        return "employee/laborant/cases/allCases";
    }

    // ========== Добавление дела ==========

    @GetMapping("/employee/laborant/cases/addCase")
    public String addCaseForm(Model model) {
        model.addAttribute("allExperts", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
        // Превью: номер и дата генерируются на сервере, в форме показываем
        // как readonly. Реальные значения проставляются при POST сервисом.
        model.addAttribute("previewCaseNumber", forensicCaseService.previewNextCaseNumber());
        model.addAttribute("previewReceiptDate", LocalDate.now());
        return "employee/laborant/cases/addCase";
    }

    @PostMapping("/employee/laborant/cases/addCase")
    public String addCase(@RequestParam(required = false) String inputDescription,
                          @RequestParam(required = false) Long inputExpertId,
                          Model model) {
        // inputCaseNumber и inputReceiptDate из формы не принимаем —
        // эти поля автоматически генерируются сервером (защита от ручной правки HTML).
        Optional<Long> result = forensicCaseService.saveCase(inputDescription, inputExpertId);

        if (result.isEmpty()) {
            model.addAttribute("caseError",
                    "Не удалось зарегистрировать дело. Попробуйте ещё раз — возможно, в этот момент " +
                            "другой лаборант создавал дело с тем же номером.");
            model.addAttribute("allExperts", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
            model.addAttribute("previewCaseNumber", forensicCaseService.previewNextCaseNumber());
            model.addAttribute("previewReceiptDate", LocalDate.now());
            return "employee/laborant/cases/addCase";
        }
        return "redirect:/employee/laborant/cases/detailsCase/" + result.get();
    }

    // ========== Просмотр дела ==========

    @GetMapping("/employee/laborant/cases/detailsCase/{id}")
    public String detailsCase(@PathVariable(value = "id") long id, Model model) {
        Optional<ForensicCaseDTO> caseOptional = forensicCaseService.getCaseById(id);
        if (caseOptional.isEmpty()) {
            return "redirect:/employee/laborant/cases/allCases";
        }
        model.addAttribute("caseDTO", caseOptional.get());
        model.addAttribute("caseSamples", sampleService.getSamplesByCase(id));
        return "employee/laborant/cases/detailsCase";
    }

    // ========== Редактирование дела ==========

    @GetMapping("/employee/laborant/cases/editCase/{id}")
    public String editCaseForm(@PathVariable(value = "id") long id, Model model) {
        Optional<ForensicCaseDTO> caseOptional = forensicCaseService.getCaseById(id);
        if (caseOptional.isEmpty()) {
            return "redirect:/employee/laborant/cases/allCases";
        }
        model.addAttribute("caseDTO", caseOptional.get());
        model.addAttribute("allExperts", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
        return "employee/laborant/cases/editCase";
    }

    @PostMapping("/employee/laborant/cases/editCase/{id}")
    public String editCase(@PathVariable(value = "id") long id,
                           @RequestParam(required = false) String inputDescription,
                           @RequestParam(required = false) Long inputExpertId,
                           RedirectAttributes redirectAttributes) {
        // inputCaseNumber и inputReceiptDate при редактировании не принимаем —
        // эти поля неизменяемы после регистрации дела.
        Optional<Long> result = forensicCaseService.editCase(id, inputDescription, inputExpertId);

        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("caseError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/laborant/cases/editCase/" + id;
        }
        return "redirect:/employee/laborant/cases/detailsCase/" + id;
    }

    // ========== Удаление дела ==========

    @GetMapping("/employee/laborant/cases/deleteCase/{id}")
    public String deleteCase(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        if (forensicCaseService.deleteCase(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Дело успешно удалено.");
            return "redirect:/employee/laborant/cases/allCases";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "Невозможно удалить дело: есть связанные образцы.");
        return "redirect:/employee/laborant/cases/detailsCase/" + id;
    }
}
