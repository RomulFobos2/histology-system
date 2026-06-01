package ru.mai.histology.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.models.AutoencoderModel;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.Role;
import ru.mai.histology.repo.AutoencoderModelRepository;
import ru.mai.histology.repo.EmployeeRepository;
import ru.mai.histology.repo.RoleRepository;

import java.time.LocalDate;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String ESRGAN_MODEL_NAME = "RealESRGAN_x4plus";
    private static final String UNET_MODEL_NAME = "histology-denoising-unet";
    private static final String BASELINE_MODEL_NAME = "baseline-pillow-enhancer";

    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final AutoencoderModelRepository autoencoderModelRepository;

    public DataInitializer(RoleRepository roleRepository,
                           EmployeeRepository employeeRepository,
                           BCryptPasswordEncoder bCryptPasswordEncoder,
                           AutoencoderModelRepository autoencoderModelRepository) {
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.autoencoderModelRepository = autoencoderModelRepository;
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

        seedAutoencoderModels();
    }

    /**
     * Создаёт записи о доступных моделях улучшения изображений.
     * Real-ESRGAN — активная по умолчанию (промышленная SOTA с готовыми весами).
     * U-Net и baseline создаются как неактивные; U-Net становится активной после
     * успешного обучения через POST /train на Python-сервисе.
     */
    private void seedAutoencoderModels() {
        if (autoencoderModelRepository.findByModelName(ESRGAN_MODEL_NAME).isEmpty()) {
            // Деактивируем все предыдущие записи перед тем, как пометить ESRGAN активной
            autoencoderModelRepository.findFirstByIsActiveTrue().ifPresent(previous -> {
                previous.setActive(false);
                autoencoderModelRepository.save(previous);
            });

            AutoencoderModel esrgan = new AutoencoderModel();
            esrgan.setModelName(ESRGAN_MODEL_NAME);
            esrgan.setDescription(
                    "Real-ESRGAN x4plus: промышленная state-of-the-art модель супер-разрешения. " +
                    "Архитектура — RRDB-генератор (Residual-in-Residual Dense Blocks, 23 блока). " +
                    "Обучена с perceptual loss (VGG19) и adversarial loss (GAN). " +
                    "Делает 4× апскейл с восстановлением мелких деталей. " +
                    "Веса предобучены сообществом, локального обучения не требует."
            );
            esrgan.setTrainedDate(LocalDate.of(2021, 9, 1));
            esrgan.setEpochs(0);
            esrgan.setLoss(0.0);
            esrgan.setValidationLoss(0.0);
            esrgan.setActive(true);
            autoencoderModelRepository.save(esrgan);
            log.info("Создана активная модель {} (Real-ESRGAN с предобученными весами)", ESRGAN_MODEL_NAME);
        }

        if (autoencoderModelRepository.findByModelName(UNET_MODEL_NAME).isEmpty()) {
            AutoencoderModel unet = new AutoencoderModel();
            unet.setModelName(UNET_MODEL_NAME);
            unet.setDescription(
                    "Собственный denoising U-Net (3 ступени, BatchNorm, ReLU, residual blend 0.35·input + 0.65·output). " +
                    "Обучается локально на гистологических изображениях с искусственной деградацией " +
                    "(blur, шум, JPEG-артефакты, потеря контраста). Loss = L1 + 0.15·edge-L1. " +
                    "Сохраняет разрешение исходного изображения."
            );
            unet.setTrainedDate(null);
            unet.setEpochs(0);
            unet.setLoss(0.0);
            unet.setValidationLoss(0.0);
            unet.setActive(false);
            autoencoderModelRepository.save(unet);
            log.info("Создана запись модели {} (наша собственная U-Net, не активна — обучите через /train)", UNET_MODEL_NAME);
        }

        if (autoencoderModelRepository.findByModelName(BASELINE_MODEL_NAME).isEmpty()) {
            AutoencoderModel baseline = new AutoencoderModel();
            baseline.setModelName(BASELINE_MODEL_NAME);
            baseline.setDescription(
                    "Базовый пайплайн улучшения на Pillow без нейросети: " +
                    "autocontrast → SHARPEN → Contrast(1.08) → Sharpness(1.15). " +
                    "Используется как безопасный fallback, когда нейросети недоступны."
            );
            baseline.setTrainedDate(LocalDate.of(2026, 4, 4));
            baseline.setEpochs(0);
            baseline.setLoss(0.0);
            baseline.setValidationLoss(0.0);
            baseline.setActive(false);
            autoencoderModelRepository.save(baseline);
            log.info("Создана запись модели {} (Pillow-fallback)", BASELINE_MODEL_NAME);
        }
    }

    private void createRoleIfNotExists(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(new Role(name, description));
            log.info("Создана роль: {} ({})", name, description);
        }
    }
}
