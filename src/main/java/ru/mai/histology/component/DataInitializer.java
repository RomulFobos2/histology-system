package ru.mai.histology.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.Role;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.RoleRepository;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public DataInitializer(RoleRepository roleRepository,
                           EmployeeRepository employeeRepository,
                           BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createRoleIfNotExists("ROLE_EMPLOYEE_ADMIN", "Администратор");
        createRoleIfNotExists("ROLE_EMPLOYEE_HEAD", "Начальник БСМЭ");
        createRoleIfNotExists("ROLE_EMPLOYEE_HISTOLOGIST", "Врач-гистолог");
        createRoleIfNotExists("ROLE_EMPLOYEE_LABORANT", "Лаборант");

        if (employeeRepository.findByUsername("admin").isEmpty()) {
            Role adminRole = roleRepository.findByName("ROLE_EMPLOYEE_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Роль ROLE_EMPLOYEE_ADMIN не найдена"));

            Employee admin = new Employee("Администратор", "Системный", "", "admin",
                    bCryptPasswordEncoder.encode("admin"));
            admin.setRole(adminRole);
            admin.setActive(true);
            admin.setNeedChangePassword(true);
            admin.setPosition("Системный администратор");
            employeeRepository.save(admin);
            log.info("Создан администратор по умолчанию: admin/admin");
        }
    }

    private void createRoleIfNotExists(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(new Role(name, description));
            log.info("Создана роль: {} ({})", name, description);
        }
    }
}
