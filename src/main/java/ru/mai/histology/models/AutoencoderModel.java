package ru.mai.histology.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_autoencoderModel")
public class AutoencoderModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String modelName;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate trainedDate;

    private Integer epochs;

    private Double loss;

    private Double validationLoss;

    @Column(nullable = false)
    private boolean isActive = false;
}
