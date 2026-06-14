package ru.mai.histology.controllers.employee.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.models.ActionLog;
import ru.mai.histology.models.Employee;
import ru.mai.histology.repo.ActionLogRepository;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.service.general.ExcelExportService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
@Slf4j
public class AdminJournalController {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final ActionLogRepository actionLogRepository;
    private final EmployeeRepository employeeRepository;
    private final ExcelExportService excelExportService;

    public AdminJournalController(ActionLogRepository actionLogRepository,
                                  EmployeeRepository employeeRepository,
                                  ExcelExportService excelExportService) {
        this.actionLogRepository = actionLogRepository;
        this.employeeRepository = employeeRepository;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/employee/admin/journal/view")
    public String view(@RequestParam(required = false) Long employeeId,
                       @RequestParam(required = false) ActionType actionType,
                       @RequestParam(required = false)
                       @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate from,
                       @RequestParam(required = false)
                       @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "50") int size,
                       Model model) {

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : null;

        Page<ActionLog> entries = actionLogRepository.findByFilters(
                employeeId, actionType, fromDt, toDt, PageRequest.of(page, size));

        model.addAttribute("entries", entries.getContent());
        model.addAttribute("currentPage", entries.getNumber());
        model.addAttribute("totalPages", entries.getTotalPages());
        model.addAttribute("totalElements", entries.getTotalElements());
        model.addAttribute("pageSize", size);

        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("actionTypes", ActionType.values());
        model.addAttribute("filterEmployeeId", employeeId);
        model.addAttribute("filterActionType", actionType);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        model.addAttribute("dateFormatter", DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        model.addAttribute("dateTimeFormatter", DATE_TIME_FMT);

        return "employee/admin/journal/view";
    }

    @PostMapping("/employee/admin/journal/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) ActionType actionType,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate to) {

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : null;

        List<ActionLog> all = actionLogRepository.findAllByFilters(employeeId, actionType, fromDt, toDt);

        List<String> headers = List.of("№", "Дата/время", "Пользователь", "Логин", "Роль",
                "Тип действия", "Описание", "IP-адрес", "Результат");

        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            ActionLog a = all.get(i);
            rows.add(List.of(
                    String.valueOf(i + 1),
                    a.getTimestamp() != null ? a.getTimestamp().format(DATE_TIME_FMT) : "",
                    a.getFullNameSnapshot() != null ? a.getFullNameSnapshot() : "—",
                    a.getUsernameSnapshot() != null ? a.getUsernameSnapshot() : "—",
                    a.getRoleSnapshot() != null ? a.getRoleSnapshot() : "—",
                    a.getActionType() != null ? a.getActionType().getDescription() : "",
                    a.getDescription() != null ? a.getDescription() : "",
                    a.getIpAddress() != null ? a.getIpAddress() : "—",
                    a.isSuccess() ? "Успех" : "Ошибка"
            ));
        }

        try {
            byte[] excel = excelExportService.generateExcel("Журнал действий", headers, rows);
            String filename = "action_journal_" + LocalDateTime.now().format(FILE_FMT) + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        } catch (Exception e) {
            log.error("Ошибка экспорта журнала действий: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class EmployeeOption {
        private final Long id;
        private final String fullName;

        public EmployeeOption(Employee e) {
            this.id = e.getId();
            this.fullName = e.getFullName();
        }

        public Long getId() { return id; }
        public String getFullName() { return fullName; }
    }
}
