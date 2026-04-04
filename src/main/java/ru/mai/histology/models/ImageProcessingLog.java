package ru.mai.histology.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_imageProcessingLog")
public class ImageProcessingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate processedDate;

    private Long processingTimeMs;

    @ManyToOne
    @JoinColumn(name = "original_image_id", nullable = false)
    @ToString.Exclude
    private MicroscopeImage originalImage;

    @ManyToOne
    @JoinColumn(name = "enhanced_image_id", nullable = false)
    @ToString.Exclude
    private MicroscopeImage enhancedImage;

    @ManyToOne
    @JoinColumn(name = "autoencoder_model_id")
    @ToString.Exclude
    private AutoencoderModel autoencoderModel;

    @ManyToOne
    @JoinColumn(name = "processed_by_id")
    @ToString.Exclude
    private Employee processedBy;
}
