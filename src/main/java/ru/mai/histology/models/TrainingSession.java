package ru.mai.histology.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_trainingSession")
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(length = 30)
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

    @Column(length = 255)
    private String modelName;

    @Column(columnDefinition = "TEXT")
    private String message;

    @ManyToOne
    @JoinColumn(name = "triggered_by_id")
    @ToString.Exclude
    private Employee triggeredBy;
}
