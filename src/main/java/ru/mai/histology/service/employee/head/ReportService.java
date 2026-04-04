package ru.mai.histology.service.employee.head;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.DashboardStatsDTO;
import ru.mai.histology.enumeration.CaseStatus;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.models.*;
import ru.mai.histology.repo.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Сервис отчётов и аналитики для начальника БСМЭ */
@Service
@Slf4j
public class ReportService {

    private final SampleRepository sampleRepository;
    private final ForensicCaseRepository forensicCaseRepository;
    private final HistologistConclusionRepository histConclusionRepository;
    private final ForensicConclusionRepository forensicConclusionRepository;
    private final ImageProcessingLogRepository imageProcessingLogRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");

    public ReportService(SampleRepository sampleRepository,
                         ForensicCaseRepository forensicCaseRepository,
                         HistologistConclusionRepository histConclusionRepository,
                         ForensicConclusionRepository forensicConclusionRepository,
                         ImageProcessingLogRepository imageProcessingLogRepository) {
        this.sampleRepository = sampleRepository;
        this.forensicCaseRepository = forensicCaseRepository;
        this.histConclusionRepository = histConclusionRepository;
        this.forensicConclusionRepository = forensicConclusionRepository;
        this.imageProcessingLogRepository = imageProcessingLogRepository;
    }

    // ========== Дашборд ==========

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // Дела
        stats.setTotalCases(forensicCaseRepository.count());
        stats.setOpenCases(forensicCaseRepository.countByStatus(CaseStatus.OPEN)
                + forensicCaseRepository.countByStatus(CaseStatus.IN_PROGRESS));
        stats.setClosedCases(forensicCaseRepository.countByStatus(CaseStatus.CONCLUDED)
                + forensicCaseRepository.countByStatus(CaseStatus.CLOSED));

        // Образцы по статусам (pie chart)
        LinkedHashMap<String, Long> statusMap = new LinkedHashMap<>();
        for (SampleStatus status : SampleStatus.values()) {
            long count = sampleRepository.countByStatus(status);
            if (count > 0) {
                statusMap.put(status.getDisplayName(), count);
            }
        }
        stats.setSamplesByStatus(statusMap);

        // Образцы за последние 30 дней (line chart)
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(29);
        List<Sample> recentSamples = sampleRepository.findAllByReceiptDateGreaterThanEqual(thirtyDaysAgo);
        Map<LocalDate, Long> dateCountMap = recentSamples.stream()
                .filter(s -> s.getReceiptDate() != null)
                .collect(Collectors.groupingBy(Sample::getReceiptDate, Collectors.counting()));

        LinkedHashMap<String, Long> last30Days = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            LocalDate date = thirtyDaysAgo.plusDays(i);
            last30Days.put(date.format(SHORT_DATE_FMT), dateCountMap.getOrDefault(date, 0L));
        }
        stats.setSamplesLast30Days(last30Days);

        // Топ-5 методов окрашивания
        List<Sample> allSamples = sampleRepository.findAll();
        LinkedHashMap<String, Long> methodMap = allSamples.stream()
                .filter(s -> s.getStainingMethod() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getStainingMethod().getDisplayName(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
        stats.setTopStainingMethods(methodMap);

        // Среднее время от поступления до заключения
        List<HistologistConclusion> allConclusions = histConclusionRepository.findAllByOrderByConclusionDateDesc();
        if (!allConclusions.isEmpty()) {
            double avgDays = allConclusions.stream()
                    .filter(c -> c.getSample().getReceiptDate() != null && c.getConclusionDate() != null)
                    .mapToLong(c -> ChronoUnit.DAYS.between(c.getSample().getReceiptDate(), c.getConclusionDate()))
                    .average()
                    .orElse(0.0);
            stats.setAvgDaysReceiptToConclusion(Math.round(avgDays * 10.0) / 10.0);
        }

        // Автоэнкодер
        stats.setAutoencoderProcessedCount(imageProcessingLogRepository.count());

        return stats;
    }

    // ========== Excel-отчёты ==========

    /** Отчёт 1: Полученные образцы за период */
    @Transactional(readOnly = true)
    public byte[] generateSamplesReceivedExcel(LocalDate from, LocalDate to) {
        List<Sample> samples = sampleRepository.findAllByReceiptDateBetweenOrderByReceiptDateDesc(from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Полученные образцы");
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Заголовок
            Row header = sheet.createRow(0);
            String[] cols = {"№", "Номер образца", "Номер дела", "Дата получения", "Тип ткани", "Метод окрашивания", "Статус"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            int rowNum = 1;
            for (Sample s : samples) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(s.getSampleNumber());
                row.createCell(2).setCellValue(s.getForensicCase().getCaseNumber());
                row.createCell(3).setCellValue(s.getReceiptDate() != null ? s.getReceiptDate().format(DATE_FMT) : "");
                row.createCell(4).setCellValue(s.getTissueType().getDisplayName());
                row.createCell(5).setCellValue(s.getStainingMethod().getDisplayName());
                row.createCell(6).setCellValue(s.getStatus().getDisplayName());
                rowNum++;
            }

            // Итоги по типу ткани
            rowNum += 2;
            Row summaryHeader = sheet.createRow(rowNum++);
            summaryHeader.createCell(0).setCellValue("Итого по типу ткани:");
            summaryHeader.getCell(0).setCellStyle(headerStyle);

            Map<String, Long> byTissue = samples.stream()
                    .collect(Collectors.groupingBy(s -> s.getTissueType().getDisplayName(), Collectors.counting()));
            for (Map.Entry<String, Long> entry : byTissue.entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            autoSizeColumns(sheet, cols.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /** Отчёт 2: Выполненные исследования за период */
    @Transactional(readOnly = true)
    public byte[] generateCompletedStudiesExcel(LocalDate from, LocalDate to) {
        List<HistologistConclusion> conclusions =
                histConclusionRepository.findAllByConclusionDateBetweenOrderByConclusionDateDesc(from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Выполненные исследования");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            String[] cols = {"№", "Номер образца", "Номер дела", "Диагноз", "Дата заключения", "Гистолог"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (HistologistConclusion c : conclusions) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(c.getSample().getSampleNumber());
                row.createCell(2).setCellValue(c.getSample().getForensicCase().getCaseNumber());
                row.createCell(3).setCellValue(c.getDiagnosis());
                row.createCell(4).setCellValue(c.getConclusionDate() != null ? c.getConclusionDate().format(DATE_FMT) : "");
                row.createCell(5).setCellValue(getEmployeeFullName(c.getHistologist()));
                rowNum++;
            }

            // Итого по экспертам
            rowNum += 2;
            Row summaryHeader = sheet.createRow(rowNum++);
            summaryHeader.createCell(0).setCellValue("Итого по экспертам:");
            summaryHeader.getCell(0).setCellStyle(headerStyle);

            Map<String, Long> byExpert = conclusions.stream()
                    .collect(Collectors.groupingBy(c -> getEmployeeFullName(c.getHistologist()), Collectors.counting()));
            for (Map.Entry<String, Long> entry : byExpert.entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
            }

            autoSizeColumns(sheet, cols.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /** Отчёт 3: Методы окрашивания за период */
    @Transactional(readOnly = true)
    public byte[] generateStainingMethodsExcel(LocalDate from, LocalDate to) {
        List<Sample> samples = sampleRepository.findAllByReceiptDateBetweenOrderByReceiptDateDesc(from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Методы окрашивания");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            String[] cols = {"Метод окрашивания", "Количество", "Процент (%)"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            Map<String, Long> methodCount = samples.stream()
                    .collect(Collectors.groupingBy(s -> s.getStainingMethod().getDisplayName(), Collectors.counting()));

            long total = samples.size();
            int rowNum = 1;
            for (Map.Entry<String, Long> entry : methodCount.entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
                row.createCell(2).setCellValue(total > 0 ? Math.round(entry.getValue() * 100.0 / total * 10.0) / 10.0 : 0);
            }

            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("ИТОГО");
            totalRow.getCell(0).setCellStyle(headerStyle);
            totalRow.createCell(1).setCellValue(total);
            totalRow.createCell(2).setCellValue(100.0);

            autoSizeColumns(sheet, cols.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /** Отчёт 4: Результаты исследований за период */
    @Transactional(readOnly = true)
    public byte[] generateResearchResultsExcel(LocalDate from, LocalDate to) {
        List<HistologistConclusion> histConclusions =
                histConclusionRepository.findAllByConclusionDateBetweenOrderByConclusionDateDesc(from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Результаты исследований");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            String[] cols = {"№", "Номер образца", "Номер дела", "Диагноз", "Заключение гистолога", "Заключение СМЭ", "Дата"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (HistologistConclusion hc : histConclusions) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(hc.getSample().getSampleNumber());
                row.createCell(2).setCellValue(hc.getSample().getForensicCase().getCaseNumber());
                row.createCell(3).setCellValue(hc.getDiagnosis());
                row.createCell(4).setCellValue(truncate(hc.getConclusionText(), 200));

                // Судебно-медицинское заключение (если есть)
                String forensicText = forensicConclusionRepository.findBySampleId(hc.getSample().getId())
                        .map(fc -> truncate(fc.getConclusionText(), 200))
                        .orElse("—");
                row.createCell(5).setCellValue(forensicText);
                row.createCell(6).setCellValue(hc.getConclusionDate() != null ? hc.getConclusionDate().format(DATE_FMT) : "");
                rowNum++;
            }

            autoSizeColumns(sheet, cols.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /** Отчёт 5: Статистика обработки изображений за период */
    @Transactional(readOnly = true)
    public byte[] generateImageProcessingStatsExcel(LocalDate from, LocalDate to) {
        List<ImageProcessingLog> logs =
                imageProcessingLogRepository.findAllByProcessedDateBetweenOrderByProcessedDateDesc(from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Обработка изображений");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            String[] cols = {"№", "Дата обработки", "Время обработки (мс)", "Модель", "Оператор"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            long totalTime = 0;
            for (ImageProcessingLog ipl : logs) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(ipl.getProcessedDate() != null ? ipl.getProcessedDate().format(DATE_FMT) : "");
                row.createCell(2).setCellValue(ipl.getProcessingTimeMs());
                row.createCell(3).setCellValue(ipl.getAutoencoderModel() != null ? ipl.getAutoencoderModel().getModelName() : "—");
                row.createCell(4).setCellValue(getEmployeeFullName(ipl.getProcessedBy()));
                totalTime += ipl.getProcessingTimeMs();
                rowNum++;
            }

            // Итого
            rowNum++;
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("ИТОГО");
            totalRow.getCell(0).setCellStyle(headerStyle);
            totalRow.createCell(1).setCellValue("Обработано: " + logs.size());
            totalRow.createCell(2).setCellValue(logs.isEmpty() ? 0 : totalTime / logs.size());
            totalRow.getCell(2).setCellStyle(headerStyle);

            autoSizeColumns(sheet, cols.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    // ========== Вспомогательные ==========

    private byte[] workbookToBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String getEmployeeFullName(Employee employee) {
        if (employee == null) return "—";
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
