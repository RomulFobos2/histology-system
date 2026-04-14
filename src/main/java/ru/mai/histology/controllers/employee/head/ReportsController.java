package ru.mai.histology.controllers.employee.head;

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
import ru.mai.histology.service.employee.head.ReportsService;
import ru.mai.histology.service.general.ExcelExportService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
public class ReportsController {

    private final ReportsService reportsService;
    private final ExcelExportService excelExportService;

    public ReportsController(ReportsService reportsService, ExcelExportService excelExportService) {
        this.reportsService = reportsService;
        this.excelExportService = excelExportService;
    }

    // ========== Дашборд ==========

    @GetMapping("/employee/head/reports/dashboard")
    public String dashboard(Model model) {
        model.addAllAttributes(reportsService.getDashboardStats());
        return "employee/head/reports/dashboard";
    }

    // ========== 1. Поступившие образцы ==========

    @GetMapping("/employee/head/reports/samplesReceived")
    public String samplesReceivedForm(Model model) {
        model.addAttribute("reportTitle", "Отчёт о поступивших образцах");
        return "employee/head/reports/samplesReceived";
    }

    @PostMapping("/employee/head/reports/samplesReceived")
    public Object samplesReceived(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(required = false) String export,
            Model model) {
        Map<String, Object> report = reportsService.getSamplesReceivedReport(dateFrom, dateTo);
        if ("excel".equals(export)) {
            return exportExcel("Поступившие образцы", report);
        }
        model.addAttribute("reportTitle", "Отчёт о поступивших образцах");
        model.addAttribute("report", report);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/head/reports/samplesReceived";
    }

    // ========== 2. Выполненные исследования ==========

    @GetMapping("/employee/head/reports/completedStudies")
    public String completedStudiesForm(Model model) {
        model.addAttribute("reportTitle", "Отчёт о выполненных исследованиях");
        return "employee/head/reports/completedStudies";
    }

    @PostMapping("/employee/head/reports/completedStudies")
    public Object completedStudies(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(required = false) String export,
            Model model) {
        Map<String, Object> report = reportsService.getCompletedStudiesReport(dateFrom, dateTo);
        if ("excel".equals(export)) {
            return exportExcel("Выполненные исследования", report);
        }
        model.addAttribute("reportTitle", "Отчёт о выполненных исследованиях");
        model.addAttribute("report", report);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/head/reports/completedStudies";
    }

    // ========== 3. Методы окрашивания ==========

    @GetMapping("/employee/head/reports/stainingMethods")
    public String stainingMethodsForm(Model model) {
        model.addAttribute("reportTitle", "Отчёт о методах окрашивания");
        return "employee/head/reports/stainingMethods";
    }

    @PostMapping("/employee/head/reports/stainingMethods")
    public Object stainingMethods(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(required = false) String export,
            Model model) {
        Map<String, Object> report = reportsService.getStainingMethodsReport(dateFrom, dateTo);
        if ("excel".equals(export)) {
            return exportExcel("Методы окрашивания", report);
        }
        model.addAttribute("reportTitle", "Отчёт о методах окрашивания");
        model.addAttribute("report", report);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/head/reports/stainingMethods";
    }

    // ========== 4. Результаты исследований ==========

    @GetMapping("/employee/head/reports/researchResults")
    public String researchResultsForm(Model model) {
        model.addAttribute("reportTitle", "Отчёт о результатах исследований");
        return "employee/head/reports/researchResults";
    }

    @PostMapping("/employee/head/reports/researchResults")
    public Object researchResults(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(required = false) String export,
            Model model) {
        Map<String, Object> report = reportsService.getResearchResultsReport(dateFrom, dateTo);
        if ("excel".equals(export)) {
            return exportExcel("Результаты исследований", report);
        }
        model.addAttribute("reportTitle", "Отчёт о результатах исследований");
        model.addAttribute("report", report);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/head/reports/researchResults";
    }

    // ========== 5. Обработка изображений ==========

    @GetMapping("/employee/head/reports/imageProcessingStats")
    public String imageProcessingStatsForm(Model model) {
        model.addAttribute("reportTitle", "Статистика обработки изображений");
        return "employee/head/reports/imageProcessingStats";
    }

    @PostMapping("/employee/head/reports/imageProcessingStats")
    public Object imageProcessingStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(required = false) String export,
            Model model) {
        Map<String, Object> report = reportsService.getImageProcessingReport(dateFrom, dateTo);
        if ("excel".equals(export)) {
            return exportExcel("Обработка изображений", report);
        }
        model.addAttribute("reportTitle", "Статистика обработки изображений");
        model.addAttribute("report", report);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        return "employee/head/reports/imageProcessingStats";
    }

    // ========== Утилиты ==========

    @SuppressWarnings("unchecked")
    private ResponseEntity<byte[]> exportExcel(String sheetName, Map<String, Object> report) {
        try {
            List<String> headers = (List<String>) report.get("headers");
            List<List<String>> rows = (List<List<String>>) report.get("rows");
            byte[] excel = excelExportService.generateExcel(sheetName, headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (Exception e) {
            log.error("Ошибка экспорта отчёта: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
