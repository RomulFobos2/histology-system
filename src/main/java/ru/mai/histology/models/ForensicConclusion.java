package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Судебно-медицинское заключение начальника БСМЭ */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_forensicConclusion")
public class ForensicConclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Текст заключения */
    @NotNull
    @Column(columnDefinition = "TEXT")
    private String conclusionText;

    /** Дата заключения */
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate conclusionDate;

    /** Является ли заключение финальным */
    private boolean isFinal;

    /** Образец, к которому относится заключение */
    @ManyToOne
    @JoinColumn(name = "sample_id", nullable = false)
    @ToString.Exclude
    private Sample sample;

    /** Начальник БСМЭ, составивший заключение */
    @ManyToOne
    @JoinColumn(name = "head_id", nullable = false)
    @ToString.Exclude
    private Employee head;
}
