package ru.mai.histology.controllers.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.service.employee.laborant.SampleService;
import ru.mai.histology.service.general.ExcelExportService;

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

    @PostMapping("/employee/laborant/journal/export")
    public ResponseEntity<byte[]> exportJournal() {
        List<SampleDTO> samples = sampleService.getAllSamples();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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
}
