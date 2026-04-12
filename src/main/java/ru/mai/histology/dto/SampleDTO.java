package ru.mai.histology.dto;

import lombok.Data;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;

import java.time.LocalDate;

@Data
public class SampleDTO {
    private Long id;
    private String sampleNumber;
    private LocalDate receiptDate;
    private TissueType tissueType;
    private String tissueTypeDisplayName;
    private StainingMethod stainingMethod;
    private String stainingMethodDisplayName;
    private SampleStatus status;
    private String statusDisplayName;
    private String notes;
    private Long caseId;
    private String caseNumber;
    private String registeredByFullName;
    private Long assignedHistologistId;
    private String assignedHistologistFullName;
}
