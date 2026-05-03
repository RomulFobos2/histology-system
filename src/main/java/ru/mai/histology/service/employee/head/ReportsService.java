package ru.mai.histology.service.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.enumeration.EnhancementQuality;
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

        // % хороших улучшений за последние 30 дней
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(30);
        List<ImageProcessingLog> recent = imageProcessingLogRepository.findAllByProcessedDateBetween(since, today);
        long recentTotal = recent.size();
        long recentRated = recent.stream()
                .filter(l -> l.getEnhancedImage() != null && l.getEnhancedImage().getEnhancementQuality() != null)
                .count();
        long goodOrExcellent = recent.stream()
                .filter(l -> l.getEnhancedImage() != null
                        && (l.getEnhancedImage().getEnhancementQuality() == EnhancementQuality.GOOD
                        || l.getEnhancedImage().getEnhancementQuality() == EnhancementQuality.EXCELLENT))
                .count();
        long goodEnhancementPercent = recentRated == 0 ? 0 : Math.round(100.0 * goodOrExcellent / recentRated);
        stats.put("goodEnhancementPercent", goodEnhancementPercent);
        stats.put("recentEnhancementsTotal", recentTotal);
        stats.put("recentEnhancementsRated", recentRated);
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

        List<List<String>> byModelRows = new ArrayList<>();
        byModel.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> byModelRows.add(List.of(e.getKey(), String.valueOf(e.getValue()))));

        // Счётчики по оценкам качества улучшения
        Map<String, Long> byQuality = logs.stream()
                .filter(l -> l.getEnhancedImage() != null && l.getEnhancedImage().getEnhancementQuality() != null)
                .collect(Collectors.groupingBy(
                        l -> l.getEnhancedImage().getEnhancementQuality().name(),
                        Collectors.counting()));
        long badCount = byQuality.getOrDefault(EnhancementQuality.BAD.name(), 0L);
        long goodCount = byQuality.getOrDefault(EnhancementQuality.GOOD.name(), 0L);
        long excellentCount = byQuality.getOrDefault(EnhancementQuality.EXCELLENT.name(), 0L);
        long ratedTotal = badCount + goodCount + excellentCount;
        long badPercent = ratedTotal == 0 ? 0 : Math.round(100.0 * badCount / ratedTotal);
        long goodPercent = ratedTotal == 0 ? 0 : Math.round(100.0 * goodCount / ratedTotal);
        long excellentPercent = ratedTotal == 0 ? 0 : Math.round(100.0 * excellentCount / ratedTotal);

        // Сводная таблица: модель × оценка
        Map<String, Map<EnhancementQuality, Long>> matrix = logs.stream()
                .filter(l -> l.getAutoencoderModel() != null
                        && l.getEnhancedImage() != null
                        && l.getEnhancedImage().getEnhancementQuality() != null)
                .collect(Collectors.groupingBy(
                        l -> l.getAutoencoderModel().getModelName(),
                        Collectors.groupingBy(
                                l -> l.getEnhancedImage().getEnhancementQuality(),
                                Collectors.counting())));
        List<List<String>> qualityMatrixRows = new ArrayList<>();
        matrix.entrySet().stream()
                .sorted((a, b) -> {
                    long sa = a.getValue().values().stream().mapToLong(Long::longValue).sum();
                    long sb = b.getValue().values().stream().mapToLong(Long::longValue).sum();
                    return Long.compare(sb, sa);
                })
                .forEach(e -> {
                    Map<EnhancementQuality, Long> qmap = e.getValue();
                    long bad = qmap.getOrDefault(EnhancementQuality.BAD, 0L);
                    long good = qmap.getOrDefault(EnhancementQuality.GOOD, 0L);
                    long excellent = qmap.getOrDefault(EnhancementQuality.EXCELLENT, 0L);
                    qualityMatrixRows.add(List.of(
                            e.getKey(),
                            String.valueOf(bad),
                            String.valueOf(good),
                            String.valueOf(excellent),
                            String.valueOf(bad + good + excellent)
                    ));
                });

        // Excel: единая таблица с разбивкой по моделям и оценкам
        List<List<String>> excelRows = new ArrayList<>(qualityMatrixRows);
        excelRows.add(List.of("", "", "", "", ""));
        excelRows.add(List.of("ИТОГО оценок:", String.valueOf(badCount), String.valueOf(goodCount),
                String.valueOf(excellentCount), String.valueOf(ratedTotal)));
        excelRows.add(List.of("Всего обработано:", String.valueOf(totalProcessed), "", "", ""));
        excelRows.add(List.of("Среднее время (мс):", String.valueOf(Math.round(avgTimeMs)), "", "", ""));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalProcessed);
        result.put("avgTimeMs", Math.round(avgTimeMs));
        result.put("byModel", byModel);
        result.put("byModelRows", byModelRows);
        result.put("byModelHeaders", List.of("Модель", "Количество обработок"));

        result.put("badCount", badCount);
        result.put("goodCount", goodCount);
        result.put("excellentCount", excellentCount);
        result.put("ratedTotal", ratedTotal);
        result.put("badPercent", badPercent);
        result.put("goodPercent", goodPercent);
        result.put("excellentPercent", excellentPercent);

        result.put("qualityMatrixRows", qualityMatrixRows);
        result.put("qualityMatrixHeaders", List.of("Модель", "Плохо", "Хорошо", "Отлично", "Всего"));

        // Для Excel-экспорта (рендерится через generic exportExcel)
        result.put("rows", excelRows);
        result.put("headers", List.of("Модель", "Плохо", "Хорошо", "Отлично", "Всего"));
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
