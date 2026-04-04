package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ResearchProtocolDTO {
    private Long id;
    private String protocolNumber;
    private LocalDate createdDate;
    private String protocolText;

    // Образец
    private Long sampleId;
    private String sampleNumber;
    private Long caseId;
    private String caseNumber;
    private String tissueTypeDisplayName;
    private String stainingMethodDisplayName;

    // Создал
    private Long createdById;
    private String createdByFullName;
}
