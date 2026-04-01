package ru.mai.histology.service.employee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import ru.mai.histology.dto.EmployeeDTO;
import ru.mai.histology.mapper.EmployeeMapper;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.Role;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.RoleRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class EmployeeService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository,
                           RoleRepository roleRepository,
                           BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Попытка загрузки пользователя: username={}", username);

        Optional<Employee> employeeOptional = employeeRepository.findByUsername(username);
        if (employeeOptional.isEmpty()) {
            log.warn("Пользователь не найден: username={}", username);
            throw new UsernameNotFoundException("Не найден сотрудник с username = " + username);
        }

        Employee employee = employeeOptional.get();
        log.debug("Пользователь найден: username={}, active={}", username, employee.isActive());
        return employee;
    }

    // ========== CRUD для Admin ==========

    @Transactional
    public Optional<Long> saveUser(String lastName, String firstName, String middleName,
                                   LocalDate birthDate, String position,
                                   String username, String password, String roleName) {
        log.info("Сохранение нового пользователя: username={}", username);

        if (employeeRepository.existsByUsername(username)) {
            log.error("Пользователь с username={} уже существует", username);
            return Optional.empty();
        }

        if (!isPasswordValid(password)) {
            log.error("Пароль не соответствует требованиям безопасности");
            return Optional.empty();
        }

        Optional<Role> roleOptional = roleRepository.findByName(roleName);
        if (roleOptional.isEmpty()) {
            log.error("Роль {} не найдена", roleName);
            return Optional.empty();
        }

        try {
            if (middleName == null || middleName.isBlank()) middleName = "";
            Employee employee = new Employee(lastName, firstName, middleName, username,
                    bCryptPasswordEncoder.encode(password));
            employee.setBirthDate(birthDate);
            employee.setPosition(position);
            employee.setRole(roleOptional.get());
            employee.setActive(true);
            employee.setNeedChangePassword(true);
            employeeRepository.save(employee);
            log.info("Пользователь сохранён: id={}, username={}", employee.getId(), username);
            return Optional.of(employee.getId());
        } catch (Exception e) {
            log.error("Ошибка при сохранении пользователя: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Long> editUser(Long id, String lastName, String firstName, String middleName,
                                   LocalDate birthDate, String position,
                                   String username, String roleName) {
        log.info("Редактирование пользователя: id={}", id);

        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            log.error("Пользователь не найден: id={}", id);
            return Optional.empty();
        }

        if (employeeRepository.existsByUsernameAndIdNot(username, id)) {
            log.error("Пользователь с username={} уже существует", username);
            return Optional.empty();
        }

        Optional<Role> roleOptional = roleRepository.findByName(roleName);
        if (roleOptional.isEmpty()) {
            log.error("Роль {} не найдена", roleName);
            return Optional.empty();
        }

        try {
            Employee employee = employeeOptional.get();
            employee.setLastName(lastName);
            employee.setFirstName(firstName);
            employee.setMiddleName(middleName == null || middleName.isBlank() ? "" : middleName);
            employee.setBirthDate(birthDate);
            employee.setPosition(position);
            employee.setUsername(username);
            employee.setRole(roleOptional.get());
            employeeRepository.save(employee);
            log.info("Пользователь обновлён: id={}", id);
            return Optional.of(id);
        } catch (Exception e) {
            log.error("Ошибка при редактировании пользователя: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }
    }

    @Transactional
    public boolean deactivateUser(Long id) {
        log.info("Деактивация пользователя: id={}", id);

        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            log.error("Пользователь не найден: id={}", id);
            return false;
        }

        try {
            Employee employee = employeeOptional.get();
            employee.setActive(false);
            employeeRepository.save(employee);
            log.info("Пользователь деактивирован: id={}, username={}", id, employee.getUsername());
            return true;
        } catch (Exception e) {
            log.error("Ошибка при деактивации: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Transactional
    public boolean activateUser(Long id) {
        log.info("Активация пользователя: id={}", id);

        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            log.error("Пользователь не найден: id={}", id);
            return false;
        }

        try {
            Employee employee = employeeOptional.get();
            employee.setActive(true);
            employeeRepository.save(employee);
            log.info("Пользователь активирован: id={}, username={}", id, employee.getUsername());
            return true;
        } catch (Exception e) {
            log.error("Ошибка при активации: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Transactional
    public boolean resetPassword(Long id, String newPassword) {
        log.info("Сброс пароля для пользователя: id={}", id);

        if (!isPasswordValid(newPassword)) {
            log.error("Пароль не соответствует требованиям безопасности");
            return false;
        }

        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            log.error("Пользователь не найден: id={}", id);
            return false;
        }

        try {
            Employee employee = employeeOptional.get();
            employee.setPassword(bCryptPasswordEncoder.encode(newPassword));
            employee.setNeedChangePassword(true);
            employeeRepository.save(employee);
            log.info("Пароль сброшен для пользователя: id={}", id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при сбросе пароля: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Валидация пароля ==========

    private boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) return false;
        return password.matches(".*[A-Z].*") && password.matches(".*\\d.*");
    }

    // ========== Смена собственного пароля ==========

    @Transactional
    public boolean changeOwnPassword(String newPassword) {
        Employee currentEmployee = getAuthenticationEmployee();
        if (currentEmployee == null) {
            log.error("Не удалось определить текущего пользователя для смены пароля");
            return false;
        }

        log.info("Смена пароля пользователем: id={}, username={}", currentEmployee.getId(), currentEmployee.getUsername());

        if (!isPasswordValid(newPassword)) {
            log.error("Пароль не соответствует требованиям безопасности");
            return false;
        }

        try {
            currentEmployee.setPassword(bCryptPasswordEncoder.encode(newPassword));
            currentEmployee.setNeedChangePassword(false);
            employeeRepository.save(currentEmployee);
            log.info("Пароль успешно изменён пользователем: id={}", currentEmployee.getId());
            return true;
        } catch (Exception e) {
            log.error("Ошибка при смене пароля: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    // ========== Проверка возможности деактивации ==========

    public String getDeactivateError(Long id) {
        Employee currentEmployee = getAuthenticationEmployee();
        if (currentEmployee != null && currentEmployee.getId().equals(id)) {
            return "Нельзя деактивировать свою учётную запись";
        }

        Optional<Employee> employeeOptional = employeeRepository.findById(id);
        if (employeeOptional.isEmpty()) {
            return "Пользователь не найден";
        }

        Employee target = employeeOptional.get();
        if ("ROLE_EMPLOYEE_ADMIN".equals(target.getRole().getName())) {
            return "Нельзя деактивировать администратора";
        }

        return null;
    }

    // ========== Запросы данных ==========

    public boolean checkUserName(String username) {
        return employeeRepository.existsByUsername(username);
    }

    public boolean checkUserNameExcluding(String username, Long id) {
        return employeeRepository.existsByUsernameAndIdNot(username, id);
    }

    public List<EmployeeDTO> getAllUsers() {
        List<Employee> employees = employeeRepository.findAllByOrderByLastNameAsc();
        return EmployeeMapper.INSTANCE.toDTOList(employees);
    }

    public Optional<EmployeeDTO> getUserById(Long id) {
        return employeeRepository.findById(id)
                .map(EmployeeMapper.INSTANCE::toDTO);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findByNameStartingWith("ROLE_EMPLOYEE");
    }

    public EmployeeDTO getAuthenticationEmployeeDTO() {
        Optional<Employee> employeeOptional = employeeRepository.findByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName());
        return employeeOptional.map(EmployeeMapper.INSTANCE::toDTO).orElse(null);
    }

    public Employee getAuthenticationEmployee() {
        return employeeRepository.findByUsername(
                SecurityContextHolder.getContext().getAuthentication().getName()).orElse(null);
    }
}
