package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ForensicConclusionDTO {
    private Long id;
    private String conclusionText;
    private LocalDate conclusionDate;
    private boolean isFinal;

    // Образец
    private Long sampleId;
    private String sampleNumber;
    private Long caseId;
    private String caseNumber;
    private String tissueTypeDisplayName;
    private String stainingMethodDisplayName;
    private String sampleStatusDisplayName;

    // Начальник
    private Long headId;
    private String headFullName;

    // Заключение гистолога (для отображения в карточке)
    private String histologistDiagnosis;
    private String histologistConclusionText;
    private String histologistFullName;
}
