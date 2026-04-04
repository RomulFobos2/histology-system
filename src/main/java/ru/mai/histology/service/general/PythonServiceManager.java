package ru.mai.histology.service.general;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Управление жизненным циклом Python-микросервиса автоэнкодера.
 * Позволяет запускать и останавливать процесс uvicorn из Spring Boot.
 */
@Service
@Slf4j
public class PythonServiceManager {

    @Value("${autoencoder.python.executable:python}")
    private String pythonExecutable;

    @Value("${autoencoder.python.workdir:autoencoder}")
    private String workdir;

    @Value("${autoencoder.service.host:127.0.0.1}")
    private String host;

    @Value("${autoencoder.service.port:8000}")
    private int port;

    private static final int MAX_WAIT_SECONDS = 30;

    private Process process;
    private final RestTemplate healthCheckTemplate = new RestTemplate();

    /**
     * Запустить Python-микросервис (uvicorn app:app).
     * @return true если процесс запущен, false если уже работает или ошибка
     */
    public synchronized boolean start() {
        if (isRunning()) {
            log.info("Python-сервис уже запущен (PID={})", process.pid());
            return false;
        }

        try {
            File workDirectory = new File(workdir).getAbsoluteFile();
            if (!workDirectory.isDirectory()) {
                log.error("Рабочая директория Python-сервиса не найдена: {}", workDirectory);
                return false;
            }

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable, "-m", "uvicorn",
                    "app:app",
                    "--host", host,
                    "--port", String.valueOf(port)
            );
            pb.directory(workDirectory);
            pb.redirectErrorStream(true);
            // Перенаправляем вывод в лог-файл
            File logFile = new File(workDirectory, "service.log");
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

            process = pb.start();
            log.info("Python-сервис запущен: PID={}, workdir={}, port={}. Ожидание готовности...", process.pid(), workDirectory, port);

            // Ждём, пока сервис начнёт отвечать на /health
            boolean ready = waitForReady();
            if (ready) {
                log.info("Python-сервис готов к работе (PID={})", process.pid());
            } else {
                log.warn("Python-сервис запущен (PID={}), но не ответил на /health за {} секунд", process.pid(), MAX_WAIT_SECONDS);
            }
            return true;
        } catch (IOException e) {
            log.error("Ошибка запуска Python-сервиса: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Остановить Python-микросервис.
     * @return true если процесс остановлен, false если не был запущен
     */
    public synchronized boolean stop() {
        if (!isRunning()) {
            log.info("Python-сервис не запущен, нечего останавливать");
            return false;
        }

        try {
            long pid = process.pid();
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor();
            process = null;
            log.info("Python-сервис остановлен (PID={})", pid);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Прерывание при остановке Python-сервиса: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверить, запущен ли процесс, управляемый этим менеджером.
     */
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /**
     * Ожидание готовности Python-сервиса (polling /health каждую секунду).
     */
    private boolean waitForReady() {
        String healthUrl = "http://" + host + ":" + port + "/health";
        for (int i = 0; i < MAX_WAIT_SECONDS; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            // Если процесс уже упал — прекращаем ждать
            if (process == null || !process.isAlive()) {
                return false;
            }
            try {
                ResponseEntity<Map> resp = healthCheckTemplate.getForEntity(healthUrl, Map.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Сервис ещё не готов, пробуем снова
            }
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        if (isRunning()) {
            log.info("Остановка Python-сервиса при завершении Spring Boot...");
            stop();
        }
    }
}
