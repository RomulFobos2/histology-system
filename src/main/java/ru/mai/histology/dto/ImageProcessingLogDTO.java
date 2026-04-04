package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ImageProcessingLogDTO {
    private Long id;
    private LocalDate processedDate;
    private Long processingTimeMs;
    private Long originalImageId;
    private Long enhancedImageId;
    private Long autoencoderModelId;
    private String autoencoderModelName;
    private Long processedById;
    private String processedByFullName;
}
