package ru.mai.histology.service.general;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.dto.NotificationDTO;
import ru.mai.voshod.pneumotraining.mapper.NotificationMapper;
import ru.mai.voshod.pneumotraining.models.Employee;
import ru.mai.voshod.pneumotraining.models.Notification;
import ru.mai.voshod.pneumotraining.repo.NotificationRepository;

import java.util.List;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsForEmployee(Long employeeId) {
        return NotificationMapper.INSTANCE.toDTOList(
                notificationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long employeeId) {
        return notificationRepository.countByEmployeeIdAndIsReadFalse(employeeId);
    }

    @Transactional
    public void createNotification(Employee employee, String message, String link) {
        Notification notification = new Notification(employee, message, link);
        notificationRepository.save(notification);
        log.debug("Уведомление создано для сотрудника id={}: {}", employee.getId(), message);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long employeeId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (notification.getEmployee().getId().equals(employeeId)) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        });
    }

    @Transactional
    public void markAllAsRead(Long employeeId) {
        List<Notification> unread = notificationRepository.findByEmployeeIdAndIsReadFalse(employeeId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        log.debug("Все уведомления отмечены прочитанными для сотрудника id={}", employeeId);
    }
}
