package ru.mai.histology.controllers.employee.general;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.voshod.pneumotraining.dto.EmployeeDTO;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.employee.EmployeeService;

@Controller
public class MainEmployeeController {

    private final EmployeeService employeeService;

    public MainEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/employee/login")
    public String employeeLogin() {
        return "employee/general/login";
    }

    @GetMapping("/employee/profile")
    public String employeeProfile(Model model) {
        EmployeeDTO currentEmployeeDTO = employeeService.getAuthenticationEmployeeDTO();
        if (currentEmployeeDTO != null) {
            model.addAttribute("currentEmployee", currentEmployeeDTO);
        }
        return "employee/general/profile";
    }

    // ========== Принудительная смена пароля ==========

    @GetMapping("/employee/change-password")
    public String changePasswordForm() {
        Employee currentEmployee = employeeService.getAuthenticationEmployee();
        if (currentEmployee == null || !currentEmployee.isNeedChangePassword()) {
            return "redirect:/";
        }
        return "employee/general/change-password";
    }

    @PostMapping("/employee/change-password")
    public String changePassword(@RequestParam String inputPassword,
                                 RedirectAttributes redirectAttributes) {
        if (employeeService.changeOwnPassword(inputPassword)) {
            return "redirect:/";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при смене пароля. Попробуйте ещё раз.");
        return "redirect:/employee/change-password";
    }
}
