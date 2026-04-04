package ru.mai.histology.service.general;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Управление жизненным циклом Python-микросервиса автоэнкодера.
 * Сервис запускается отдельным detached-процессом через PowerShell Start-Process,
 * чтобы не получать консольные сигналы от Spring Boot / IDE во время долгого обучения.
 */
@Service
@Slf4j
public class PythonServiceManager {

    @Value("${autoencoder.python.executable:}")
    private String pythonExecutable;

    @Value("${autoencoder.python.workdir:autoencoder}")
    private String workdir;

    @Value("${autoencoder.service.host:127.0.0.1}")
    private String host;

    @Value("${autoencoder.service.port:8000}")
    private int port;

    @Value("${autoencoder.python.stop-on-shutdown:false}")
    private boolean stopOnShutdown;

    private static final int MAX_WAIT_SECONDS = 30;

    private Long managedPid;
    private boolean externalServiceDetected;
    private RestTemplate healthCheckTemplate;

    @PostConstruct
    private void initHealthCheckTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        healthCheckTemplate = new RestTemplate(factory);
        log.debug("healthCheckTemplate инициализирован с таймаутами connect=2s read=3s");
    }

    public synchronized boolean start() {
        if (isServiceAvailable()) {
            externalServiceDetected = managedPid == null || !isManagedProcessAlive();
            log.info("Python-сервис уже доступен на {}:{}, новый экземпляр не запускается", host, port);
            return true;
        }

        if (isRunning()) {
            log.info("Python-сервис уже запущен этим менеджером (PID={})", managedPid);
            return false;
        }

        try {
            File workDirectory = new File(workdir).getAbsoluteFile();
            if (!workDirectory.isDirectory()) {
                log.error("Рабочая директория Python-сервиса не найдена: {}", workDirectory);
                return false;
            }

            String resolvedPythonExecutable = resolvePythonExecutable(workDirectory);
            if (resolvedPythonExecutable == null) {
                log.error("Не удалось определить Python-интерпретатор для автоэнкодера. "
                        + "Ожидался venv внутри {} или настройка autoencoder.python.executable", workDirectory);
                return false;
            }

            Long startedPid = startDetachedPythonService(workDirectory, resolvedPythonExecutable);
            if (startedPid == null) {
                log.error("Не удалось получить PID запущенного Python-сервиса");
                return false;
            }

            managedPid = startedPid;
            externalServiceDetected = false;
            log.info("Python-сервис запущен detached-процессом: PID={}, python={}, workdir={}, port={}. "
                    + "Статус будет проверен при следующем открытии дашборда.",
                    managedPid, resolvedPythonExecutable, workDirectory, port);
            return true;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка запуска Python-сервиса: {}", e.getMessage(), e);
            stopProcessSilently();
            return false;
        }
    }

    public synchronized boolean stop() {
        if (!isRunning()) {
            if (externalServiceDetected && isServiceAvailable()) {
                log.info("Python-сервис доступен, но не был запущен этим менеджером. Автоматическая остановка пропущена.");
                return false;
            }

            log.info("Python-сервис не запущен, останавливать нечего");
            return false;
        }

        Optional<ProcessHandle> handle = getManagedHandle();
        if (handle.isEmpty()) {
            managedPid = null;
            externalServiceDetected = false;
            return false;
        }

        long pid = handle.get().pid();
        handle.get().descendants().forEach(ProcessHandle::destroyForcibly);
        handle.get().destroyForcibly();
        managedPid = null;
        externalServiceDetected = false;
        log.info("Python-сервис остановлен (PID={})", pid);
        return true;
    }

    public synchronized boolean isRunning() {
        return isManagedProcessAlive();
    }

    public synchronized boolean isServiceAvailable() {
        String healthUrl = "http://" + host + ":" + port + "/health";
        try {
            ResponseEntity<Map> response = healthCheckTemplate.getForEntity(healthUrl, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized boolean isExternallyManaged() {
        return externalServiceDetected && !isRunning() && isServiceAvailable();
    }

    private Long startDetachedPythonService(File workDirectory, String resolvedPythonExecutable)
            throws IOException, InterruptedException {

        String logPath = new File(workDirectory, "service.log").getAbsolutePath().replace("'", "''");
        String pythonPath = resolvedPythonExecutable.replace("'", "''");
        String workingDir = workDirectory.getAbsolutePath().replace("'", "''");
        String command = String.format(
                "$p = Start-Process -FilePath '%s' -WorkingDirectory '%s'"
                        + " -ArgumentList '-m','uvicorn','app:app','--host','%s','--port','%s'"
                        + " -RedirectStandardOutput '%s' -RedirectStandardError '%s'"
                        + " -WindowStyle Hidden -PassThru; $p.Id",
                pythonPath,
                workingDir,
                host,
                port,
                logPath,
                logPath + ".err"
        );

        Process launcher = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                command
        ).start();

        boolean finished = launcher.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            launcher.destroyForcibly();
            log.error("PowerShell не завершился за 15 секунд при запуске Python-сервиса");
            return null;
        }

        String stdout = new String(launcher.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(launcher.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

        if (launcher.exitValue() != 0) {
            log.error("Не удалось detached-запустить Python-сервис. stderr: {}", stderr);
            return null;
        }

        try {
            return Long.parseLong(stdout.lines().reduce((first, second) -> second).orElse(stdout).trim());
        } catch (NumberFormatException e) {
            log.error("Не удалось распарсить PID detached-процесса. stdout: '{}', stderr: '{}'", stdout, stderr);
            return null;
        }
    }

    private boolean isManagedProcessAlive() {
        return getManagedHandle().map(ProcessHandle::isAlive).orElse(false);
    }

    private Optional<ProcessHandle> getManagedHandle() {
        if (managedPid == null) {
            return Optional.empty();
        }
        return ProcessHandle.of(managedPid).filter(ProcessHandle::isAlive);
    }

    private String resolvePythonExecutable(File workDirectory) {
        if (pythonExecutable != null && !pythonExecutable.isBlank()) {
            return pythonExecutable;
        }

        Path workPath = workDirectory.toPath();
        Path windowsVenvPython = workPath.resolve("venv").resolve("Scripts").resolve("python.exe");
        if (Files.isRegularFile(windowsVenvPython)) {
            return windowsVenvPython.toAbsolutePath().toString();
        }

        Path unixVenvPython = workPath.resolve("venv").resolve("bin").resolve("python");
        if (Files.isRegularFile(unixVenvPython)) {
            return unixVenvPython.toAbsolutePath().toString();
        }

        return null;
    }

    private void logServiceLogTail(Path workDirectory) {
        Path logPath = workDirectory.resolve("service.log");
        if (!Files.isRegularFile(logPath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(logPath);
            int fromIndex = Math.max(lines.size() - 20, 0);
            List<String> tail = lines.subList(fromIndex, lines.size());
            log.error("Последние строки service.log перед отказом запуска Python-сервиса:{}{}",
                    System.lineSeparator(),
                    String.join(System.lineSeparator(), tail));
        } catch (IOException e) {
            log.warn("Не удалось прочитать service.log: {}", e.getMessage());
        }
    }

    private void stopProcessSilently() {
        getManagedHandle().ifPresent(handle -> {
            handle.descendants().forEach(ProcessHandle::destroyForcibly);
            handle.destroyForcibly();
        });
        managedPid = null;
        externalServiceDetected = false;
    }

    @PreDestroy
    public void shutdown() {
        if (!stopOnShutdown) {
            log.info("Автоматическая остановка Python-сервиса при shutdown Spring отключена "
                    + "(autoencoder.python.stop-on-shutdown=false)");
            return;
        }

        if (isRunning()) {
            log.info("Остановка Python-сервиса при завершении Spring Boot...");
            stop();
        }
    }
}
