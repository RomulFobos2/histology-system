package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class JournalEntryDTO {
    private Long sampleId;
    private String caseNumber;
    private String sampleNumber;
    private LocalDate receiptDate;
    private String tissueTypeDisplayName;
    private String stainingMethodDisplayName;
    private String researchStageDisplayName;
    private String expertFullName;
    private String statusDisplayName;
}
