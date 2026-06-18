package ru.mai.histology.controllers.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.service.employee.laborant.ForensicCaseService;
import ru.mai.histology.service.employee.laborant.SampleService;
import ru.mai.histology.service.general.FileStorageService;

import java.io.IOException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class ForensicCaseController {

    private static final long PROTOCOL_PDF_MAX_BYTES = 10L * 1024 * 1024;

    private final ForensicCaseService forensicCaseService;
    private final SampleService sampleService;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;

    public ForensicCaseController(ForensicCaseService forensicCaseService,
                                  SampleService sampleService,
                                  EmployeeRepository employeeRepository,
                                  FileStorageService fileStorageService) {
        this.forensicCaseService = forensicCaseService;
        this.sampleService = sampleService;
        this.employeeRepository = employeeRepository;
        this.fileStorageService = fileStorageService;
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

    @PostMapping(value = "/employee/laborant/cases/addCase", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public String addCase(@RequestParam(required = false) String inputDescription,
                          @RequestParam(required = false) Long inputExpertId,
                          @RequestParam(required = false)
                          @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate inputAutopsyDate,
                          @RequestParam(required = false)
                          @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate inputSamplingDate,
                          @RequestParam(required = false) String inputPersonFullName,
                          @RequestParam(required = false) Integer inputBirthYear,
                          @RequestParam(required = false) MultipartFile inputProtocolPdf,
                          Model model) {
        String validationError = validateExtraFields(LocalDate.now(), inputAutopsyDate, inputSamplingDate, inputPersonFullName, inputBirthYear);
        if (validationError != null) {
            model.addAttribute("caseError", validationError);
            model.addAttribute("allExperts", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
            model.addAttribute("previewCaseNumber", forensicCaseService.previewNextCaseNumber());
            model.addAttribute("previewReceiptDate", LocalDate.now());
            return "employee/laborant/cases/addCase";
        }

        String protocolPdfPath = null;
        if (inputProtocolPdf != null && !inputProtocolPdf.isEmpty()) {
            String pdfError = validateProtocolPdf(inputProtocolPdf);
            if (pdfError != null) {
                model.addAttribute("caseError", pdfError);
                model.addAttribute("allExperts", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
                model.addAttribute("previewCaseNumber", forensicCaseService.previewNextCaseNumber());
                model.addAttribute("previewReceiptDate", LocalDate.now());
                return "employee/laborant/cases/addCase";
            }
            try {
                String caseNumberForPath = forensicCaseService.previewNextCaseNumber()
                        .replaceAll("[/\\\\]", "_");
                protocolPdfPath = fileStorageService.saveProtocolPdf(inputProtocolPdf.getBytes(), caseNumberForPath);
            } catch (IOException e) {
                log.error("Не удалось прочитать PDF-протокол: {}", e.getMessage(), e);
                model.addAttribute("caseError", "Не удалось прочитать PDF-файл протокола.");
                model.addAttribute("allExperts", employeeRepository.findAllByRole_Name("ROLE_EMPLOYEE_HISTOLOGIST"));
                model.addAttribute("previewCaseNumber", forensicCaseService.previewNextCaseNumber());
                model.addAttribute("previewReceiptDate", LocalDate.now());
                return "employee/laborant/cases/addCase";
            }
        }

        Optional<Long> result = forensicCaseService.saveCase(inputDescription, inputExpertId,
                inputAutopsyDate, inputSamplingDate, inputPersonFullName != null ? inputPersonFullName.trim() : null, inputBirthYear,
                protocolPdfPath);

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

    @GetMapping("/employee/laborant/cases/protocol/{id}")
    public ResponseEntity<byte[]> downloadProtocol(@PathVariable long id) {
        return getProtocolBytes(id);
    }

    @GetMapping("/employee/head/oversight/protocol/{id}")
    public ResponseEntity<byte[]> downloadProtocolForHead(@PathVariable long id) {
        return getProtocolBytes(id);
    }

    private ResponseEntity<byte[]> getProtocolBytes(long id) {
        Optional<ForensicCaseDTO> caseOpt = forensicCaseService.getCaseById(id);
        if (caseOpt.isEmpty() || caseOpt.get().getProtocolPdfPath() == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = fileStorageService.readFile(caseOpt.get().getProtocolPdfPath());
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = "protocol_" + caseOpt.get().getCaseNumber().replaceAll("[/\\\\]", "_") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private String validateProtocolPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "Файл не загружен.";
        }
        if (file.getSize() > PROTOCOL_PDF_MAX_BYTES) {
            return "Размер PDF-протокола превышает 10 МБ.";
        }
        String contentType = file.getContentType();
        String name = file.getOriginalFilename();
        boolean okType = "application/pdf".equalsIgnoreCase(contentType)
                || (name != null && name.toLowerCase().endsWith(".pdf"));
        if (!okType) {
            return "Допускаются только файлы PDF.";
        }
        return null;
    }

    private String validateExtraFields(LocalDate receiptDate,
                                       LocalDate autopsyDate, LocalDate samplingDate,
                                       String personFullName, Integer birthYear) {
        if (autopsyDate == null) {
            return "Укажите дату вскрытия.";
        }
        if (samplingDate == null) {
            return "Укажите дату вырезки.";
        }
        if (receiptDate != null && autopsyDate.isBefore(receiptDate)) {
            return "Дата вскрытия не может быть раньше даты поступления.";
        }
        if (receiptDate != null && samplingDate.isBefore(receiptDate)) {
            return "Дата вырезки не может быть раньше даты поступления.";
        }
        if (personFullName == null || personFullName.trim().isEmpty()) {
            return "Укажите ФИО.";
        }
        if (birthYear == null) {
            return "Укажите год рождения.";
        }
        int currentYear = LocalDate.now().getYear();
        if (birthYear < 1900 || birthYear > currentYear) {
            return "Год рождения должен быть в диапазоне 1900 — " + currentYear + ".";
        }
        return null;
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
                           @RequestParam(required = false)
                           @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate inputAutopsyDate,
                           @RequestParam(required = false)
                           @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate inputSamplingDate,
                           @RequestParam(required = false) String inputPersonFullName,
                           @RequestParam(required = false) Integer inputBirthYear,
                           RedirectAttributes redirectAttributes) {
        LocalDate receiptDate = forensicCaseService.getCaseById(id)
                .map(ForensicCaseDTO::getReceiptDate)
                .orElse(null);
        String validationError = validateExtraFields(receiptDate, inputAutopsyDate, inputSamplingDate, inputPersonFullName, inputBirthYear);
        if (validationError != null) {
            redirectAttributes.addFlashAttribute("caseError", validationError);
            return "redirect:/employee/laborant/cases/editCase/" + id;
        }

        Optional<Long> result = forensicCaseService.editCase(id, inputDescription, inputExpertId,
                inputAutopsyDate, inputSamplingDate, inputPersonFullName != null ? inputPersonFullName.trim() : null, inputBirthYear);

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
