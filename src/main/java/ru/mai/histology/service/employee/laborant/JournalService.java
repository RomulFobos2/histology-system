package ru.mai.histology.service.employee.laborant;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mai.histology.dto.JournalEntryDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.Sample;
import ru.mai.histology.repo.SampleRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Сервис электронного журнала лаборанта */
@Service
@Slf4j
public class JournalService {

    private final SampleRepository sampleRepository;

    public JournalService(SampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    /** Получить все записи журнала (образцы с информацией о деле и сотрудниках) */
    @Transactional(readOnly = true)
    public List<JournalEntryDTO> getJournalEntries() {
        List<Sample> samples = sampleRepository.findAllWithCaseAndEmployees();
        List<JournalEntryDTO> entries = new ArrayList<>();
        for (Sample s : samples) {
            JournalEntryDTO dto = new JournalEntryDTO();
            dto.setSampleId(s.getId());
            dto.setCaseNumber(s.getForensicCase().getCaseNumber());
            dto.setSampleNumber(s.getSampleNumber());
            dto.setReceiptDate(s.getReceiptDate());
            dto.setTissueTypeDisplayName(s.getTissueType().getDisplayName());
            dto.setStainingMethodDisplayName(s.getStainingMethod().getDisplayName());
            dto.setResearchStageDisplayName(s.getResearchStage().getDisplayName());
            dto.setExpertFullName(getEmployeeFullName(s.getAssignedHistologist()));
            dto.setStatusDisplayName(s.getStatus().getDisplayName());
            entries.add(dto);
        }
        return entries;
    }

    /** Экспорт журнала в Excel */
    @Transactional(readOnly = true)
    public byte[] generateJournalExcel() {
        List<JournalEntryDTO> entries = getJournalEntries();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Журнал");

            // Стиль заголовков
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Заголовки
            String[] headers = {
                    "№", "Номер дела", "Номер образца", "Дата поступления",
                    "Тип ткани", "Метод окрашивания", "Стадия исследования",
                    "Эксперт", "Статус"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            int rowNum = 1;
            for (JournalEntryDTO entry : entries) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(entry.getCaseNumber());
                row.createCell(2).setCellValue(entry.getSampleNumber());
                row.createCell(3).setCellValue(
                        entry.getReceiptDate() != null ? entry.getReceiptDate().format(dtf) : "");
                row.createCell(4).setCellValue(entry.getTissueTypeDisplayName());
                row.createCell(5).setCellValue(entry.getStainingMethodDisplayName());
                row.createCell(6).setCellValue(entry.getResearchStageDisplayName());
                row.createCell(7).setCellValue(entry.getExpertFullName() != null ? entry.getExpertFullName() : "—");
                row.createCell(8).setCellValue(entry.getStatusDisplayName());
                rowNum++;
            }

            // Автоширина
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            return workbookToBytes(workbook);
        } catch (IOException e) {
            log.error("Ошибка при генерации Excel журнала", e);
            return new byte[0];
        }
    }

    // ========== Helpers ==========

    private String getEmployeeFullName(Employee e) {
        if (e == null) return null;
        String full = e.getLastName() + " " + e.getFirstName();
        if (e.getMiddleName() != null && !e.getMiddleName().isBlank()) {
            full += " " + e.getMiddleName();
        }
        return full;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private byte[] workbookToBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}
