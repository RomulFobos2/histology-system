package ru.mai.histology.controllers.general;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

@ControllerAdvice
public class NotificationControllerAdvice {

    private final NotificationService notificationService;

    public NotificationControllerAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("unreadNotificationCount")
    public Long unreadNotificationCount(@AuthenticationPrincipal Employee currentUser) {
        if (currentUser == null) {
            return 0L;
        }
        return notificationService.getUnreadCount(currentUser.getId());
    }
}
