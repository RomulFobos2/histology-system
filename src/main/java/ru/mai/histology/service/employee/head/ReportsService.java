package ru.mai.histology.service.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.models.*;
import ru.mai.histology.repo.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportsService {

    private final SampleRepository sampleRepository;
    private final HistologistConclusionRepository histConclusionRepository;
    private final ForensicConclusionRepository forensicConclusionRepository;
    private final ImageProcessingLogRepository imageProcessingLogRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public ReportsService(SampleRepository sampleRepository,
                          HistologistConclusionRepository histConclusionRepository,
                          ForensicConclusionRepository forensicConclusionRepository,
                          ImageProcessingLogRepository imageProcessingLogRepository) {
        this.sampleRepository = sampleRepository;
        this.histConclusionRepository = histConclusionRepository;
        this.forensicConclusionRepository = forensicConclusionRepository;
        this.imageProcessingLogRepository = imageProcessingLogRepository;
    }

    // ========== Дашборд ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSamples", sampleRepository.count());
        stats.put("totalConclusions", histConclusionRepository.count());
        stats.put("totalForensicConclusions", forensicConclusionRepository.count());
        stats.put("totalImageProcessed", imageProcessingLogRepository.count());
        return stats;
    }

    // ========== 1. Поступившие образцы ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getSamplesReceivedReport(LocalDate from, LocalDate to) {
        List<Sample> samples = sampleRepository.findAllByReceiptDateBetween(from, to);

        Map<String, Long> byTissueType = samples.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getTissueType().getDisplayName(),
                        Collectors.counting()));

        List<List<String>> rows = new ArrayList<>();
        byTissueType.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> rows.add(List.of(e.getKey(), String.valueOf(e.getValue()))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", samples.size());
        result.put("byTissueType", byTissueType);
        result.put("rows", rows);
        result.put("headers", List.of("Тип ткани", "Количество"));
        return result;
    }

    // ========== 2. Выполненные исследования ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getCompletedStudiesReport(LocalDate from, LocalDate to) {
        List<HistologistConclusion> conclusions = histConclusionRepository.findAllByConclusionDateBetween(from, to);

        Map<String, Long> byHistologist = conclusions.stream()
                .collect(Collectors.groupingBy(
                        c -> formatEmployee(c.getHistologist()),
                        Collectors.counting()));

        List<List<String>> rows = new ArrayList<>();
        byHistologist.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> rows.add(List.of(e.getKey(), String.valueOf(e.getValue()))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", conclusions.size());
        result.put("byHistologist", byHistologist);
        result.put("rows", rows);
        result.put("headers", List.of("Эксперт", "Количество"));
        return result;
    }

    // ========== 3. Методы окрашивания ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getStainingMethodsReport(LocalDate from, LocalDate to) {
        List<Sample> samples = sampleRepository.findAllByReceiptDateBetween(from, to);

        Map<String, Long> byMethod = samples.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStainingMethod().getDisplayName(),
                        Collectors.counting()));

        List<List<String>> rows = new ArrayList<>();
        byMethod.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> rows.add(List.of(e.getKey(), String.valueOf(e.getValue()))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", samples.size());
        result.put("byMethod", byMethod);
        result.put("rows", rows);
        result.put("headers", List.of("Метод окрашивания", "Количество"));
        return result;
    }

    // ========== 4. Результаты исследований ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getResearchResultsReport(LocalDate from, LocalDate to) {
        List<HistologistConclusion> histConclusions = histConclusionRepository.findAllByConclusionDateBetween(from, to);
        List<ForensicConclusion> forensicConclusions = forensicConclusionRepository.findAllByConclusionDateBetween(from, to);

        List<List<String>> rows = new ArrayList<>();
        for (HistologistConclusion hc : histConclusions) {
            rows.add(List.of(
                    hc.getSample() != null ? hc.getSample().getSampleNumber() : "—",
                    hc.getDiagnosis() != null ? hc.getDiagnosis() : "—",
                    formatEmployee(hc.getHistologist()),
                    hc.getConclusionDate() != null ? hc.getConclusionDate().format(DATE_FMT) : "—"
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalHist", histConclusions.size());
        result.put("totalForensic", forensicConclusions.size());
        result.put("rows", rows);
        result.put("headers", List.of("Образец", "Диагноз", "Эксперт", "Дата"));
        return result;
    }

    // ========== 5. Обработка изображений ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getImageProcessingReport(LocalDate from, LocalDate to) {
        List<ImageProcessingLog> logs = imageProcessingLogRepository.findAllByProcessedDateBetween(from, to);

        long totalProcessed = logs.size();
        double avgTimeMs = logs.stream()
                .filter(l -> l.getProcessingTimeMs() != null)
                .mapToLong(ImageProcessingLog::getProcessingTimeMs)
                .average().orElse(0);

        Map<String, Long> byModel = logs.stream()
                .filter(l -> l.getAutoencoderModel() != null)
                .collect(Collectors.groupingBy(
                        l -> l.getAutoencoderModel().getModelName(),
                        Collectors.counting()));

        List<List<String>> rows = new ArrayList<>();
        byModel.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> rows.add(List.of(e.getKey(), String.valueOf(e.getValue()))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalProcessed);
        result.put("avgTimeMs", Math.round(avgTimeMs));
        result.put("byModel", byModel);
        result.put("rows", rows);
        result.put("headers", List.of("Модель", "Количество обработок"));
        return result;
    }

    private String formatEmployee(Employee e) {
        if (e == null) return "—";
        String name = e.getLastName() + " " + e.getFirstName();
        if (e.getMiddleName() != null && !e.getMiddleName().isEmpty()) {
            name += " " + e.getMiddleName();
        }
        return name;
    }
}
