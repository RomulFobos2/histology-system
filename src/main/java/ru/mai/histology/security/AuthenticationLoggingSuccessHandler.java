package ru.mai.histology.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.models.Employee;
import ru.mai.histology.service.general.ActionLogService;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationLoggingSuccessHandler implements AuthenticationSuccessHandler {

    private final ActionLogService actionLogService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        String roleNames = authentication.getAuthorities().toString();
        String remoteAddress = resolveIp(request);

        log.info("Успешная авторизация сотрудника: username={}, roles={}, remoteAddress={}",
                username, roleNames, remoteAddress);

        try {
            actionLogService.logAuth(ActionType.LOGIN_SUCCESS, authentication, username, remoteAddress, true,
                    "Успешный вход в систему: " + username);
        } catch (Exception ex) {
            log.warn("Не удалось сохранить запись аудита LOGIN_SUCCESS: {}", ex.getMessage());
        }

        if (request.getSession(false) != null) {
            log.debug("Сессия после успешной авторизации: username={}, sessionId={}",
                    username, request.getSession(false).getId());
        }

        // Если пароль назначен администратором — перенаправить на страницу смены пароля
        if (authentication.getPrincipal() instanceof Employee employee && employee.isNeedChangePassword()) {
            log.info("Пользователь {} перенаправлен на смену пароля", username);
            response.sendRedirect("/employee/change-password");
            return;
        }

        response.sendRedirect("/");
    }

    private String resolveIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
