package ru.mai.histology.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.service.general.ActionLogService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationLoggingFailureHandler implements AuthenticationFailureHandler {

    private final ActionLogService actionLogService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String remoteAddress = resolveIp(request);

        log.warn("Неуспешная авторизация сотрудника: username={}, remoteAddress={}, reason={}",
                username, remoteAddress, exception.getMessage());

        try {
            actionLogService.logAuth(ActionType.LOGIN_FAILURE, null, username, remoteAddress, false,
                    "Неудачная попытка входа: " + username + " — " + exception.getMessage());
        } catch (Exception ex) {
            log.warn("Не удалось сохранить запись аудита LOGIN_FAILURE: {}", ex.getMessage());
        }

        String encodedError = UriUtils.encode("loginError", StandardCharsets.UTF_8);
        response.sendRedirect("/employee/login?error=" + encodedError);
    }

    private String resolveIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
