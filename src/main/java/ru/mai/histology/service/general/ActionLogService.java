package ru.mai.histology.service.general;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.models.ActionLog;
import ru.mai.histology.models.Employee;
import ru.mai.histology.repo.ActionLogRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionLogService {

    private final ActionLogRepository actionLogRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(ActionType type, String entityType, Long entityId, String description) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Employee employee = null;
        if (auth != null && auth.getPrincipal() instanceof Employee e) {
            employee = e;
        }
        saveLog(type, employee, auth != null ? auth.getName() : null, entityType, entityId, description, resolveIpAddress(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuth(ActionType type, Authentication authentication, String usernameFallback, String ipAddress, boolean success, String description) {
        Employee employee = null;
        String username = usernameFallback;
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof Employee e) {
                employee = e;
            }
            if (authentication.getName() != null) {
                username = authentication.getName();
            }
        }
        saveLog(type, employee, username, "Employee", employee != null ? employee.getId() : null, description, ipAddress, success);
    }

    private void saveLog(ActionType type, Employee employee, String username, String entityType, Long entityId, String description, String ip, boolean success) {
        ActionLog logEntry = new ActionLog();
        logEntry.setActionType(type);
        logEntry.setEmployee(employee);
        logEntry.setUsernameSnapshot(username);
        if (employee != null) {
            logEntry.setFullNameSnapshot(employee.getFullName());
            if (employee.getRole() != null) {
                logEntry.setRoleSnapshot(employee.getRole().getDescription());
            }
        }
        logEntry.setEntityType(entityType);
        logEntry.setEntityId(entityId);
        logEntry.setDescription(truncate(description, 500));
        logEntry.setIpAddress(ip);
        logEntry.setSuccess(success);
        actionLogRepository.save(logEntry);
    }

    private String resolveIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                return header.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception ex) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @SuppressWarnings("unused")
    private static String firstRole(Authentication auth) {
        if (auth == null) return null;
        for (GrantedAuthority a : auth.getAuthorities()) {
            return a.getAuthority();
        }
        return null;
    }
}
