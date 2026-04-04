package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AutoencoderModelDTO {
    private Long id;
    private String modelName;
    private String description;
    private LocalDate trainedDate;
    private Integer epochs;
    private Double loss;
    private Double validationLoss;
    private Double psnr;
    private Double ssim;
    private boolean active;
}
