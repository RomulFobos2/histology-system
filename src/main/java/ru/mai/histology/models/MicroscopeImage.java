package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_microscopeImage")
public class MicroscopeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(length = 255)
    private String originalFilename;

    @NotNull
    @Column(length = 255)
    private String storedFilename;

    @NotNull
    @Column(length = 500)
    private String filePath;

    private Long fileSize;

    @Column(length = 100)
    private String contentType;

    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate uploadDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean isEnhanced = false;

    @Column(length = 50)
    private String magnification;

    @ManyToOne
    @JoinColumn(name = "sample_id", nullable = false)
    @ToString.Exclude
    private Sample sample;

    @ManyToOne
    @JoinColumn(name = "uploaded_by_id")
    @ToString.Exclude
    private Employee uploadedBy;

    @ManyToOne
    @JoinColumn(name = "original_image_id")
    @ToString.Exclude
    private MicroscopeImage originalImage;
}
