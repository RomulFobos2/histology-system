package ru.mai.histology.controllers.employee.general;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

@Controller
@RequestMapping("/employee/notifications")
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String notifications(@AuthenticationPrincipal Employee currentUser, Model model) {
        model.addAttribute("notifications", notificationService.getNotificationsForEmployee(currentUser.getId()));
        return "employee/general/notifications";
    }

    @PostMapping("/markRead/{id}")
    public String markRead(@PathVariable Long id, @AuthenticationPrincipal Employee currentUser) {
        notificationService.markAsRead(id, currentUser.getId());
        return "redirect:/employee/notifications";
    }

    @PostMapping("/markAllRead")
    public String markAllRead(@AuthenticationPrincipal Employee currentUser) {
        notificationService.markAllAsRead(currentUser.getId());
        return "redirect:/employee/notifications";
    }
}
