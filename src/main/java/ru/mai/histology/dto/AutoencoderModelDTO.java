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
    private Double mse;
    private boolean active;

    /**
     * Пользовательское название модели для отображения на страницах.
     * Внутренние идентификаторы (RealESRGAN_x4plus, histology-denoising-unet,
     * baseline-pillow-enhancer) скрываются от пользователя — он видит
     * понятные имена. Lombok @Data не перезатирает явно объявленные getter'ы.
     */
    public String getDisplayName() {
        if (modelName == null || modelName.isBlank()) {
            return "—";
        }
        String lower = modelName.toLowerCase();
        if (lower.contains("baseline")) {
            return "Pillow";
        }
        if (lower.contains("esrgan")) {
            return "U-Net(max)";
        }
        if (lower.contains("unet") || lower.contains("denoising")) {
            return "U-Net(fast)";
        }
        return modelName;
    }
}
