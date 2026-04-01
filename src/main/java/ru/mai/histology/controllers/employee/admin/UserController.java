package ru.mai.histology.controllers.employee.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.EmployeeDTO;
import ru.mai.histology.service.employee.EmployeeService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class UserController {

    private final EmployeeService employeeService;

    public UserController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // ========== AJAX проверка уникальности username ==========

    @GetMapping("/employee/admin/users/check-username")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username,
                                                              @RequestParam(required = false) Long id) {
        boolean exists;
        if (id != null) {
            exists = employeeService.checkUserNameExcluding(username, id);
        } else {
            exists = employeeService.checkUserName(username);
        }
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    // ========== Список пользователей ==========

    @GetMapping("/employee/admin/users/allUsers")
    public String allUsers(Model model) {
        model.addAttribute("allUsers", employeeService.getAllUsers());
        return "employee/admin/users/allUsers";
    }

    // ========== Добавление пользователя ==========

    @GetMapping("/employee/admin/users/addUser")
    public String addUserForm(Model model) {
        model.addAttribute("allRoles", employeeService.getAllRoles());
        return "employee/admin/users/addUser";
    }

    @PostMapping("/employee/admin/users/addUser")
    public String addUser(@RequestParam String inputLastName,
                          @RequestParam String inputFirstName,
                          @RequestParam(required = false) String inputMiddleName,
                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate inputBirthDate,
                          @RequestParam(required = false) String inputPosition,
                          @RequestParam String inputUsername,
                          @RequestParam String inputPassword,
                          @RequestParam String inputRole,
                          Model model) {
        Optional<Long> result = employeeService.saveUser(inputLastName, inputFirstName, inputMiddleName,
                inputBirthDate, inputPosition, inputUsername, inputPassword, inputRole);

        if (result.isEmpty()) {
            model.addAttribute("userError", "Ошибка при сохранении. Возможно, логин уже занят.");
            model.addAttribute("allRoles", employeeService.getAllRoles());
            return "employee/admin/users/addUser";
        }
        return "redirect:/employee/admin/users/detailsUser/" + result.get();
    }

    // ========== Просмотр пользователя ==========

    @GetMapping("/employee/admin/users/detailsUser/{id}")
    public String detailsUser(@PathVariable(value = "id") long id, Model model) {
        Optional<EmployeeDTO> userOptional = employeeService.getUserById(id);
        if (userOptional.isEmpty()) {
            return "redirect:/employee/admin/users/allUsers";
        }
        model.addAttribute("userDTO", userOptional.get());
        model.addAttribute("canDeactivate", employeeService.getDeactivateError(id) == null);
        return "employee/admin/users/detailsUser";
    }

    // ========== Редактирование пользователя ==========

    @GetMapping("/employee/admin/users/editUser/{id}")
    public String editUserForm(@PathVariable(value = "id") long id, Model model) {
        Optional<EmployeeDTO> userOptional = employeeService.getUserById(id);
        if (userOptional.isEmpty()) {
            return "redirect:/employee/admin/users/allUsers";
        }
        model.addAttribute("userDTO", userOptional.get());
        model.addAttribute("allRoles", employeeService.getAllRoles());
        return "employee/admin/users/editUser";
    }

    @PostMapping("/employee/admin/users/editUser/{id}")
    public String editUser(@PathVariable(value = "id") long id,
                           @RequestParam String inputLastName,
                           @RequestParam String inputFirstName,
                           @RequestParam(required = false) String inputMiddleName,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate inputBirthDate,
                           @RequestParam(required = false) String inputPosition,
                           @RequestParam String inputUsername,
                           @RequestParam String inputRole,
                           RedirectAttributes redirectAttributes) {
        Optional<Long> result = employeeService.editUser(id, inputLastName, inputFirstName, inputMiddleName,
                inputBirthDate, inputPosition, inputUsername, inputRole);

        if (result.isEmpty()) {
            redirectAttributes.addFlashAttribute("userError", "Ошибка при сохранении изменений.");
            return "redirect:/employee/admin/users/editUser/" + id;
        }
        return "redirect:/employee/admin/users/detailsUser/" + id;
    }

    // ========== Деактивация / Активация ==========

    @GetMapping("/employee/admin/users/deactivateUser/{id}")
    public String deactivateUser(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        String error = employeeService.getDeactivateError(id);
        if (error != null) {
            redirectAttributes.addFlashAttribute("errorMessage", error);
            return "redirect:/employee/admin/users/detailsUser/" + id;
        }
        if (employeeService.deactivateUser(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Пользователь деактивирован.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при деактивации.");
        }
        return "redirect:/employee/admin/users/detailsUser/" + id;
    }

    @GetMapping("/employee/admin/users/activateUser/{id}")
    public String activateUser(@PathVariable(value = "id") long id, RedirectAttributes redirectAttributes) {
        if (employeeService.activateUser(id)) {
            redirectAttributes.addFlashAttribute("successMessage", "Пользователь активирован.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при активации.");
        }
        return "redirect:/employee/admin/users/detailsUser/" + id;
    }

    // ========== Сброс пароля ==========

    @GetMapping("/employee/admin/users/resetPassword/{id}")
    public String resetPasswordForm(@PathVariable(value = "id") long id, Model model) {
        Optional<EmployeeDTO> userOptional = employeeService.getUserById(id);
        if (userOptional.isEmpty()) {
            return "redirect:/employee/admin/users/allUsers";
        }
        model.addAttribute("userDTO", userOptional.get());
        return "employee/admin/users/resetPassword";
    }

    @PostMapping("/employee/admin/users/resetPassword/{id}")
    public String resetPassword(@PathVariable(value = "id") long id,
                                @RequestParam String inputPassword,
                                RedirectAttributes redirectAttributes) {
        if (employeeService.resetPassword(id, inputPassword)) {
            redirectAttributes.addFlashAttribute("successMessage", "Пароль успешно сброшен.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при сбросе пароля.");
        }
        return "redirect:/employee/admin/users/detailsUser/" + id;
    }
}
