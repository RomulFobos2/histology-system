package ru.mai.histology.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.voshod.pneumotraining.enumeration.AssignmentStatus;
import ru.mai.voshod.pneumotraining.models.SimulationAssignmentEmployee;
import ru.mai.voshod.pneumotraining.models.TestAssignmentEmployee;
import ru.mai.voshod.pneumotraining.repo.SimulationAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.repo.TestAssignmentEmployeeRepository;
import ru.mai.voshod.pneumotraining.service.general.NotificationService;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class NotificationScheduler {

    private final TestAssignmentEmployeeRepository testAssignmentEmployeeRepository;
    private final SimulationAssignmentEmployeeRepository simulationAssignmentEmployeeRepository;
    private final NotificationService notificationService;

    public NotificationScheduler(TestAssignmentEmployeeRepository testAssignmentEmployeeRepository,
                                 SimulationAssignmentEmployeeRepository simulationAssignmentEmployeeRepository,
                                 NotificationService notificationService) {
        this.testAssignmentEmployeeRepository = testAssignmentEmployeeRepository;
        this.simulationAssignmentEmployeeRepository = simulationAssignmentEmployeeRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendDeadlineReminders() {
        log.info("Запуск отправки напоминаний о дедлайнах");
        LocalDate today = LocalDate.now();
        String testLink = "/employee/specialist/testing/availableTests";
        String simulationLink = "/employee/specialist/mnemo/scenarios";

        sendTestRemindersForDate(today.plusDays(5), "До дедлайна осталось 5 дней", testLink);
        sendTestRemindersForDate(today.plusDays(1), "До дедлайна остался 1 день", testLink);
        sendTestRemindersForDate(today, "Сегодня последний день сдачи теста", testLink);

        sendSimulationRemindersForDate(today.plusDays(5), "До дедлайна осталось 5 дней", simulationLink);
        sendSimulationRemindersForDate(today.plusDays(1), "До дедлайна остался 1 день", simulationLink);
        sendSimulationRemindersForDate(today, "Сегодня последний день сдачи сценария", simulationLink);

        log.info("Отправка напоминаний завершена");
    }

    @jakarta.annotation.PostConstruct
    public void markOverdueOnStartup() {
        log.info("Проверка просроченных назначений при запуске приложения");
        markOverdueAssignments();
    }

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void markOverdueAssignments() {
        log.info("Запуск проверки просроченных назначений");
        LocalDate today = LocalDate.now();
        String testLink = "/employee/specialist/testing/availableTests";
        String simulationLink = "/employee/specialist/mnemo/scenarios";

        List<TestAssignmentEmployee> overdueTests = testAssignmentEmployeeRepository
                .findByStatusAndAssignment_DeadlineBefore(AssignmentStatus.PENDING, today);
        List<SimulationAssignmentEmployee> overdueSimulations = simulationAssignmentEmployeeRepository
                .findByStatusAndAssignment_DeadlineBefore(AssignmentStatus.PENDING, today);

        for (TestAssignmentEmployee ae : overdueTests) {
            ae.setStatus(AssignmentStatus.OVERDUE);
            testAssignmentEmployeeRepository.save(ae);

            notificationService.createNotification(ae.getEmployee(),
                    "Срок сдачи теста «" + ae.getAssignment().getTest().getTitle() + "» истёк",
                    testLink);
        }

        for (SimulationAssignmentEmployee ae : overdueSimulations) {
            ae.setStatus(AssignmentStatus.OVERDUE);
            simulationAssignmentEmployeeRepository.save(ae);

            notificationService.createNotification(ae.getEmployee(),
                    "Срок сдачи сценария «" + ae.getAssignment().getScenario().getTitle() + "» истёк",
                    simulationLink);
        }

        log.info("Помечено просроченных назначений: tests={}, simulations={}",
                overdueTests.size(), overdueSimulations.size());
    }

    private void sendTestRemindersForDate(LocalDate deadline, String messagePrefix, String link) {
        List<TestAssignmentEmployee> assignments = testAssignmentEmployeeRepository
                .findByStatusAndAssignment_Deadline(AssignmentStatus.PENDING, deadline);

        for (TestAssignmentEmployee ae : assignments) {
            String testTitle = ae.getAssignment().getTest().getTitle();
            notificationService.createNotification(ae.getEmployee(),
                    messagePrefix + ": тест «" + testTitle + "»",
                    link);
        }

        if (!assignments.isEmpty()) {
            log.info("Отправлено напоминаний по тестам для даты {}: {}", deadline, assignments.size());
        }
    }

    private void sendSimulationRemindersForDate(LocalDate deadline, String messagePrefix, String link) {
        List<SimulationAssignmentEmployee> assignments = simulationAssignmentEmployeeRepository
                .findByStatusAndAssignment_Deadline(AssignmentStatus.PENDING, deadline);

        for (SimulationAssignmentEmployee ae : assignments) {
            String scenarioTitle = ae.getAssignment().getScenario().getTitle();
            notificationService.createNotification(ae.getEmployee(),
                    messagePrefix + ": сценарий «" + scenarioTitle + "»",
                    link);
        }

        if (!assignments.isEmpty()) {
            log.info("Отправлено напоминаний по сценариям для даты {}: {}", deadline, assignments.size());
        }
    }
}
