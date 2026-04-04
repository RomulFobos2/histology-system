package ru.mai.histology.controllers.employee.head;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.DashboardStatsDTO;
import ru.mai.histology.service.employee.head.ReportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Контроллер отчётов и дашборда начальника БСМЭ */
@Controller
@RequestMapping("/employee/head/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // ==================== Дашборд ====================

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStatsDTO stats = reportService.getDashboardStats();
        model.addAttribute("stats", stats);
        return "employee/head/reports/dashboard";
    }

    // ==================== Отчёт 1: Поступившие образцы ====================

    @GetMapping("/samplesReceived")
    public String samplesReceivedForm() {
        return "employee/head/reports/samplesReceived";
    }

    @PostMapping("/samplesReceived")
    public ResponseEntity<byte[]> samplesReceivedExcel(
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo,
            RedirectAttributes ra) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        byte[] excel = reportService.generateSamplesReceivedExcel(from, to);
        return buildExcelResponse(excel, "otchet_obrazcy_" + dateFrom + "_" + dateTo + ".xlsx");
    }

    // ==================== Отчёт 2: Выполненные исследования ====================

    @GetMapping("/completedStudies")
    public String completedStudiesForm() {
        return "employee/head/reports/completedStudies";
    }

    @PostMapping("/completedStudies")
    public ResponseEntity<byte[]> completedStudiesExcel(
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        byte[] excel = reportService.generateCompletedStudiesExcel(from, to);
        return buildExcelResponse(excel, "otchet_issledovaniya_" + dateFrom + "_" + dateTo + ".xlsx");
    }

    // ==================== Отчёт 3: Методы окрашивания ====================

    @GetMapping("/stainingMethods")
    public String stainingMethodsForm() {
        return "employee/head/reports/stainingMethods";
    }

    @PostMapping("/stainingMethods")
    public ResponseEntity<byte[]> stainingMethodsExcel(
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        byte[] excel = reportService.generateStainingMethodsExcel(from, to);
        return buildExcelResponse(excel, "otchet_metody_okrashivaniya_" + dateFrom + "_" + dateTo + ".xlsx");
    }

    // ==================== Отчёт 4: Результаты исследований ====================

    @GetMapping("/researchResults")
    public String researchResultsForm() {
        return "employee/head/reports/researchResults";
    }

    @PostMapping("/researchResults")
    public ResponseEntity<byte[]> researchResultsExcel(
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        byte[] excel = reportService.generateResearchResultsExcel(from, to);
        return buildExcelResponse(excel, "otchet_rezultaty_" + dateFrom + "_" + dateTo + ".xlsx");
    }

    // ==================== Отчёт 5: Статистика обработки изображений ====================

    @GetMapping("/imageProcessingStats")
    public String imageProcessingStatsForm() {
        return "employee/head/reports/imageProcessingStats";
    }

    @PostMapping("/imageProcessingStats")
    public ResponseEntity<byte[]> imageProcessingStatsExcel(
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo) {
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        byte[] excel = reportService.generateImageProcessingStatsExcel(from, to);
        return buildExcelResponse(excel, "otchet_obrabotka_izobrazheniy_" + dateFrom + "_" + dateTo + ".xlsx");
    }

    // ==================== Helpers ====================

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date);
    }

    private ResponseEntity<byte[]> buildExcelResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}
