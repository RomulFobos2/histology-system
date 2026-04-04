package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingSessionDTO {
    private Long id;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
    private Integer imageSize;
    private Integer datasetSize;
    private Double loss;
    private Double validationLoss;
    private Double psnr;
    private Double ssim;
    private String modelName;
    private String message;
    private Long triggeredById;
    private String triggeredByFullName;
}
