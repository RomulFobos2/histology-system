package ru.mai.histology.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class AuthenticationLoggingFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String remoteAddress = request.getRemoteAddr();

        log.warn("Неуспешная авторизация сотрудника: username={}, remoteAddress={}, reason={}",
                username, remoteAddress, exception.getMessage());

        String encodedError = UriUtils.encode("loginError", StandardCharsets.UTF_8);
        response.sendRedirect("/employee/login?error=" + encodedError);
    }
}
