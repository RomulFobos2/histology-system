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
 * Запуск и остановка Python-сервиса автоэнкодера.
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
    /** PID-файл в папке Python-сервиса. */
    private Path pidFile;

    @PostConstruct
    private void initHealthCheckTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        healthCheckTemplate = new RestTemplate(factory);

        // Берем полный путь к PID-файлу
        pidFile = new File(workdir).getAbsoluteFile().toPath().resolve("service.pid");
        log.info("PID-файл: {}", pidFile);

        // Подхватываем PID, если сервис уже был запущен
        restorePidFromFile();
    }

    /**
     * Читаем PID из autoencoder/service.pid.
     */
    private void restorePidFromFile() {
        if (!Files.isRegularFile(pidFile)) {
            return;
        }
        try {
            String content = Files.readString(pidFile).trim();
            long pid = Long.parseLong(content);
            Optional<ProcessHandle> handle = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
            if (handle.isPresent()) {
                // Проверяем, что PID относится к Python
                boolean isPython = handle.get().info().command()
                        .map(cmd -> cmd.toLowerCase().contains("python"))
                        .orElse(false);
                if (!isPython) {
                    log.info("PID={} занят не Python-процессом, удаляем PID-файл", pid);
                    Files.deleteIfExists(pidFile);
                    return;
                }
                managedPid = pid;
                log.info("PID={} восстановлен из файла {}", pid, pidFile);
            } else {
                log.info("PID={} уже не найден, удаляем PID-файл {}", pid, pidFile);
                Files.deleteIfExists(pidFile);
            }
        } catch (NumberFormatException e) {
            log.warn("Некорректное содержимое PID-файла {}: {}", pidFile, e.getMessage());
            deletePidFileSilently();
        } catch (IOException e) {
            log.warn("Не удалось прочитать PID-файл {}: {}", pidFile, e.getMessage());
        }
    }

    private void writePidFile(long pid) {
        try {
            Files.writeString(pidFile, String.valueOf(pid));
            boolean exists = Files.exists(pidFile);
            log.info("PID={} записан в {}, файл есть: {}", pid, pidFile, exists);
        } catch (IOException e) {
            log.error("Не удалось записать PID={} в {}: {}", pid, pidFile, e.getMessage());
        }
    }

    private void deletePidFileSilently() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            log.warn("Не удалось удалить PID-файл {}: {}", pidFile, e.getMessage());
        }
    }

    public synchronized boolean start() {
        log.info("Запуск Python-сервиса, managedPid={}, pidFile={}", managedPid, pidFile);

        if (isServiceAvailable()) {
            externalServiceDetected = managedPid == null || !isManagedProcessAlive();
            log.info("Python-сервис уже доступен на {}:{}, внешний запуск: {}",
                    host, port, externalServiceDetected);
            return true;
        }

        if (isRunning()) {
            log.info("Python-сервис уже запущен этим менеджером, PID={}", managedPid);
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
                log.error("Python-интерпретатор не найден в {}", workDirectory);
                return false;
            }

            log.info("Запускаем Python-сервис: python={}, workdir={}", resolvedPythonExecutable, workDirectory);
            Long startedPid = startDetachedPythonService(workDirectory, resolvedPythonExecutable);
            if (startedPid == null) {
                log.error("Python-сервис не запущен, PID не получен");
                return false;
            }

            managedPid = startedPid;
            externalServiceDetected = false;
            // PID-файл уже записан при запуске
            log.info("Python-сервис запущен, PID={}, pidFile={}, файл есть: {}",
                    managedPid, pidFile, Files.exists(pidFile));
            return true;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка запуска Python-сервиса: {}", e.getMessage(), e);
            stopProcessSilently();
            return false;
        }
    }

    public synchronized boolean stop() {
        // Если PID известен, останавливаем процесс сразу
        Optional<ProcessHandle> handle = getManagedHandle();
        if (handle.isPresent()) {
            long pid = handle.get().pid();
            handle.get().descendants().forEach(ProcessHandle::destroyForcibly);
            handle.get().destroyForcibly();
            managedPid = null;
            externalServiceDetected = false;
            deletePidFileSilently();
            log.info("Python-сервис остановлен (PID={})", pid);
            return true;
        }

        // Если PID потеряли, ищем процесс по порту
        if (isServiceAvailable()) {
            Long foundPid = findPidByPort(port);
            if (foundPid != null) {
                Optional<ProcessHandle> foundHandle = ProcessHandle.of(foundPid).filter(ProcessHandle::isAlive);
                if (foundHandle.isPresent()) {
                    foundHandle.get().descendants().forEach(ProcessHandle::destroyForcibly);
                    foundHandle.get().destroyForcibly();
                    managedPid = null;
                    externalServiceDetected = false;
                    deletePidFileSilently();
                    log.info("Python-сервис остановлен по порту {} (PID={})", port, foundPid);
                    return true;
                }
            }
            log.warn("Python-сервис доступен на порту {}, но не удалось определить PID процесса", port);
            return false;
        }

        log.info("Python-сервис не запущен, останавливать нечего");
        managedPid = null;
        externalServiceDetected = false;
        deletePidFileSilently();
        return false;
    }

    /**
     * Ищем PID по порту.
     */
    private Long findPidByPort(int targetPort) {
        try {
            Process proc = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command",
                    String.format("(Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue).OwningProcess | Select-Object -First 1", targetPort)
            ).start();
            boolean finished = proc.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return null;
            }
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (output.isEmpty() || proc.exitValue() != 0) {
                return null;
            }
            long pid = Long.parseLong(output);
            log.debug("Найден PID={} на порту {}", pid, targetPort);
            return pid;
        } catch (IOException | InterruptedException | NumberFormatException e) {
            log.debug("Не удалось найти PID по порту {}: {}", targetPort, e.getMessage());
            return null;
        }
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
        String pidPath = pidFile.toAbsolutePath().toString().replace("'", "''");

        // PowerShell пишет PID сразу в файл
        String command = String.format(
                "$p = Start-Process -FilePath '%s' -WorkingDirectory '%s'"
                        + " -ArgumentList 'run.py','--host','%s','--port','%s'"
                        + " -RedirectStandardOutput '%s' -RedirectStandardError '%s'"
                        + " -WindowStyle Hidden -PassThru;"
                        + " $p.Id | Out-File -FilePath '%s' -Encoding ascii -NoNewline",
                pythonPath,
                workingDir,
                host,
                port,
                logPath,
                logPath + ".err",
                pidPath
        );

        log.debug("Команда запуска Python-сервиса: {}", command);

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                command
        );
        // Вывод PowerShell не читаем, PID берем из файла
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process launcher = pb.start();

        boolean finished = launcher.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            launcher.destroyForcibly();
            log.error("PowerShell не завершился за 15 секунд при запуске Python-сервиса");
            return null;
        }

        if (launcher.exitValue() != 0) {
            log.error("PowerShell завершился с ошибкой (exit={})", launcher.exitValue());
            return null;
        }

        // Проверяем, что PID-файл появился
        if (!Files.isRegularFile(pidFile)) {
            log.error("PowerShell не создал PID-файл {}", pidFile);
            return null;
        }

        try {
            String pidContent = Files.readString(pidFile).trim();
            long pid = Long.parseLong(pidContent);
            log.info("PID={} прочитан из файла {}", pid, pidFile);
            return pid;
        } catch (NumberFormatException | IOException e) {
            log.error("Не удалось прочитать PID из файла {}: {}", pidFile, e.getMessage());
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
        deletePidFileSilently();
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
