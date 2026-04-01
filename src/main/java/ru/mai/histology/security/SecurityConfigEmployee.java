package ru.mai.histology.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import ru.mai.histology.service.employee.EmployeeService;

@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityConfigEmployee {

    private final EmployeeService employeeService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final AuthenticationLoggingSuccessHandler authenticationLoggingSuccessHandler;
    private final AuthenticationLoggingFailureHandler authenticationLoggingFailureHandler;

    public SecurityConfigEmployee(EmployeeService employeeService,
                                  BCryptPasswordEncoder bCryptPasswordEncoder,
                                  AuthenticationLoggingSuccessHandler authenticationLoggingSuccessHandler,
                                  AuthenticationLoggingFailureHandler authenticationLoggingFailureHandler) {
        this.employeeService = employeeService;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.authenticationLoggingSuccessHandler = authenticationLoggingSuccessHandler;
        this.authenticationLoggingFailureHandler = authenticationLoggingFailureHandler;
    }

    @Bean
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityMatcher("/employee/**", "/static/**", "/css/**", "/js/**", "/", "/logout", "/files/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/static/**", "/css/**", "/js/**", "/", "/files/**").permitAll()
                        .requestMatchers("/employee/admin/**").hasRole("EMPLOYEE_ADMIN")
                        .requestMatchers("/employee/head/**").hasRole("EMPLOYEE_HEAD")
                        .requestMatchers("/employee/histologist/**").hasRole("EMPLOYEE_HISTOLOGIST")
                        .requestMatchers("/employee/laborant/**").hasRole("EMPLOYEE_LABORANT")
                        .requestMatchers("/employee/**").hasAnyRole("EMPLOYEE_ADMIN", "EMPLOYEE_HEAD", "EMPLOYEE_HISTOLOGIST", "EMPLOYEE_LABORANT")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/employee/login")
                        .loginProcessingUrl("/employee/login")
                        .successHandler(authenticationLoggingSuccessHandler)
                        .failureHandler(authenticationLoggingFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout.permitAll().logoutSuccessUrl("/"))
                .exceptionHandling(eh -> eh.accessDeniedHandler(accessDeniedHandler))
                .authenticationProvider(employeeAuthenticationProvider());
        return http.build();
    }

    @Bean
    public AuthenticationProvider employeeAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(employeeService);
        provider.setPasswordEncoder(bCryptPasswordEncoder);
        return provider;
    }
}
