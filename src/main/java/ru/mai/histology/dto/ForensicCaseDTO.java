package ru.mai.histology.dto;

import lombok.Data;
import ru.mai.histology.enumeration.CaseStatus;

import java.time.LocalDate;

@Data
public class ForensicCaseDTO {
    private Long id;
    private String caseNumber;
    private LocalDate receiptDate;
    private String description;
    private CaseStatus status;
    private String statusDisplayName;
    private Long expertId;
    private String expertFullName;
    private long sampleCount;
}
