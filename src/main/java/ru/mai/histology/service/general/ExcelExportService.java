package ru.mai.histology.service.general;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

    public byte[] generateExcel(String sheetName, List<String> headers, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            // Стиль заголовков
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Данные
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> rowData = rows.get(r);
                for (int c = 0; c < rowData.size(); c++) {
                    row.createCell(c).setCellValue(rowData.get(c));
                }
            }

            // Автоширина колонок
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}
