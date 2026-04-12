package ru.mai.histology.service.general;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Service;
import ru.mai.histology.dto.ForensicConclusionDTO;
import ru.mai.histology.dto.ResearchProtocolDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.format.DateTimeFormatter;

@Service
public class WordExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] exportProtocol(ResearchProtocolDTO dto) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            setPageA4(doc);

            addCenteredBold(doc, "ПРОТОКОЛ ИССЛЕДОВАНИЯ", 16);
            addCenteredBold(doc, "№ " + dto.getProtocolNumber(), 14);
            addEmptyLine(doc);

            addField(doc, "Дата", dto.getCreatedDate() != null ? dto.getCreatedDate().format(DATE_FMT) : "—");
            addField(doc, "Дело №", dto.getCaseNumber());
            addField(doc, "Образец №", dto.getSampleNumber());
            addField(doc, "Тип ткани", dto.getTissueTypeDisplayName());
            addField(doc, "Метод окрашивания", dto.getStainingMethodDisplayName());
            addField(doc, "Автор", dto.getCreatedByFullName());
            addEmptyLine(doc);

            addBoldParagraph(doc, "Текст протокола:");
            addParagraph(doc, dto.getProtocolText());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    public byte[] exportConclusion(ForensicConclusionDTO dto) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            setPageA4(doc);

            addCenteredBold(doc, "СУДЕБНО-МЕДИЦИНСКОЕ ЗАКЛЮЧЕНИЕ", 16);
            addEmptyLine(doc);

            addField(doc, "Дата", dto.getConclusionDate() != null ? dto.getConclusionDate().format(DATE_FMT) : "—");
            addField(doc, "Дело №", dto.getCaseNumber());
            addField(doc, "Образец №", dto.getSampleNumber());
            addField(doc, "Тип ткани", dto.getTissueTypeDisplayName());
            addField(doc, "Метод окрашивания", dto.getStainingMethodDisplayName());
            addField(doc, "Статус", dto.isFinal() ? "Финальное" : "Предварительное");
            addField(doc, "Эксперт", dto.getHeadFullName());
            addEmptyLine(doc);

            if (dto.getHistologistDiagnosis() != null && !dto.getHistologistDiagnosis().isEmpty()) {
                addBoldParagraph(doc, "Диагноз гистолога:");
                addParagraph(doc, dto.getHistologistDiagnosis());
                addEmptyLine(doc);
            }

            if (dto.getHistologistConclusionText() != null && !dto.getHistologistConclusionText().isEmpty()) {
                addBoldParagraph(doc, "Заключение гистолога (" + (dto.getHistologistFullName() != null ? dto.getHistologistFullName() : "") + "):");
                addParagraph(doc, dto.getHistologistConclusionText());
                addEmptyLine(doc);
            }

            addBoldParagraph(doc, "Текст заключения:");
            addParagraph(doc, dto.getConclusionText());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private void setPageA4(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(11906)); // A4 width in twips
        pageSz.setH(BigInteger.valueOf(16838)); // A4 height in twips
    }

    private void addCenteredBold(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(fontSize);
        run.setFontFamily("Times New Roman");
    }

    private void addBoldParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(12);
        run.setFontFamily("Times New Roman");
    }

    private void addField(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun labelRun = p.createRun();
        labelRun.setText(label + ": ");
        labelRun.setBold(true);
        labelRun.setFontSize(12);
        labelRun.setFontFamily("Times New Roman");
        XWPFRun valueRun = p.createRun();
        valueRun.setText(value != null ? value : "—");
        valueRun.setFontSize(12);
        valueRun.setFontFamily("Times New Roman");
    }

    private void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setFontSize(12);
        run.setFontFamily("Times New Roman");
        if (text != null) {
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                run.setText(lines[i]);
                if (i < lines.length - 1) {
                    run.addBreak();
                }
            }
        }
    }

    private void addEmptyLine(XWPFDocument doc) {
        doc.createParagraph();
    }
}
