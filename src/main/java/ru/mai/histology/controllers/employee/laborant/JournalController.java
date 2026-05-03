package ru.mai.histology.controllers.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.service.employee.laborant.SampleService;
import ru.mai.histology.service.general.ExcelExportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
@Slf4j
public class JournalController {

    private final SampleService sampleService;
    private final ExcelExportService excelExportService;

    public JournalController(SampleService sampleService, ExcelExportService excelExportService) {
        this.sampleService = sampleService;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/employee/laborant/journal/view")
    public String viewJournal(Model model) {
        model.addAttribute("allSamples", sampleService.getAllSamples());
        return "employee/laborant/journal/view";
    }

    /**
     * Экспорт журнала в Excel с учётом активных клиентских фильтров.
     * Все параметры опциональны: пустое значение = не фильтровать по этому полю.
     */
    @PostMapping("/employee/laborant/journal/export")
    public ResponseEntity<byte[]> exportJournal(
            @RequestParam(required = false) String tissue,
            @RequestParam(required = false) String staining,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        String tissueFilter = isBlank(tissue) ? null : tissue.trim();
        String stainingFilter = isBlank(staining) ? null : staining.trim();
        String statusFilter = isBlank(status) ? null : status.trim();
        String searchLower = isBlank(search) ? null : search.trim().toLowerCase();

        List<SampleDTO> samples = sampleService.getAllSamples().stream()
                .filter(s -> tissueFilter == null
                        || tissueFilter.equals(s.getTissueTypeDisplayName()))
                .filter(s -> stainingFilter == null
                        || stainingFilter.equals(s.getStainingMethodDisplayName()))
                .filter(s -> statusFilter == null
                        || statusFilter.equals(s.getStatusDisplayName()))
                .filter(s -> searchLower == null
                        || (s.getCaseNumber() != null && s.getCaseNumber().toLowerCase().contains(searchLower))
                        || (s.getSampleNumber() != null && s.getSampleNumber().toLowerCase().contains(searchLower)))
                .filter(s -> dateFrom == null
                        || (s.getReceiptDate() != null && !s.getReceiptDate().isBefore(dateFrom)))
                .filter(s -> dateTo == null
                        || (s.getReceiptDate() != null && !s.getReceiptDate().isAfter(dateTo)))
                .toList();

        List<String> headers = List.of("№", "№ дела", "№ образца", "Дата поступления",
                "Тип ткани", "Метод окрашивания", "Эксперт", "Статус");

        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            SampleDTO s = samples.get(i);
            rows.add(List.of(
                    String.valueOf(i + 1),
                    s.getCaseNumber() != null ? s.getCaseNumber() : "",
                    s.getSampleNumber() != null ? s.getSampleNumber() : "",
                    s.getReceiptDate() != null ? s.getReceiptDate().format(fmt) : "",
                    s.getTissueTypeDisplayName() != null ? s.getTissueTypeDisplayName() : "",
                    s.getStainingMethodDisplayName() != null ? s.getStainingMethodDisplayName() : "",
                    s.getAssignedHistologistFullName() != null ? s.getAssignedHistologistFullName() : "—",
                    s.getStatusDisplayName() != null ? s.getStatusDisplayName() : ""
            ));
        }

        try {
            byte[] excel = excelExportService.generateExcel("Журнал", headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"journal.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (Exception e) {
            log.error("Ошибка экспорта журнала: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
