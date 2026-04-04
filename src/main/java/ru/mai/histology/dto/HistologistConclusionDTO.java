package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HistologistConclusionDTO {
    private Long id;
    private String microscopicDescription;
    private String diagnosis;
    private String conclusionText;
    private LocalDate conclusionDate;

    // Образец
    private Long sampleId;
    private String sampleNumber;
    private Long caseId;
    private String caseNumber;
    private String tissueTypeDisplayName;
    private String stainingMethodDisplayName;

    // Гистолог
    private Long histologistId;
    private String histologistFullName;
}
