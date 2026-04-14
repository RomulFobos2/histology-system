package ru.mai.histology.controllers.employee.general;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.EmployeeDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.service.employee.EmployeeService;

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

    @GetMapping("/employee/general/profile")
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

    // ========== Добровольная смена пароля на странице профиля ==========

    @PostMapping("/employee/general/profile/change-password")
    public String changePasswordFromProfile(@RequestParam String currentPassword,
                                            @RequestParam String newPassword,
                                            @RequestParam String confirmPassword,
                                            RedirectAttributes redirectAttributes) {
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("passwordError", "Заполните все поля формы.");
            return "redirect:/employee/general/profile";
        }

        if (!employeeService.verifyCurrentPassword(currentPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Текущий пароль указан неверно.");
            return "redirect:/employee/general/profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Новый пароль и подтверждение не совпадают.");
            return "redirect:/employee/general/profile";
        }

        if (currentPassword.equals(newPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Новый пароль должен отличаться от текущего.");
            return "redirect:/employee/general/profile";
        }

        if (!employeeService.changeOwnPassword(newPassword)) {
            redirectAttributes.addFlashAttribute("passwordError",
                    "Пароль не соответствует требованиям: минимум 8 символов, хотя бы одна заглавная буква и одна цифра.");
            return "redirect:/employee/general/profile";
        }

        redirectAttributes.addFlashAttribute("passwordSuccess", "Пароль успешно изменён.");
        return "redirect:/employee/general/profile";
    }
}
