package ru.mai.histology.dto;

import lombok.Data;
import ru.mai.histology.enumeration.EnhancementQuality;

import java.time.LocalDate;

@Data
public class MicroscopeImageDTO {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private Long fileSize;
    private String fileSizeFormatted;
    private String contentType;
    private LocalDate uploadDate;
    private String description;
    private boolean enhanced;
    private EnhancementQuality enhancementQuality;
    private String enhancementQualityDisplayName;
    private String magnification;
    private Long sampleId;
    private String sampleNumber;
    private Long caseId;
    private String caseNumber;
    private String tissueTypeDisplayName;
    private String stainingMethodDisplayName;
    private Long assignedHistologistId;
    private String uploadedByFullName;
    private Long originalImageId;
}
