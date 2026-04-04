package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Заключение врача-гистолога */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_histologistConclusion")
public class HistologistConclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Микроскопическое описание */
    @NotNull
    @Column(columnDefinition = "TEXT")
    private String microscopicDescription;

    /** Диагноз */
    @NotNull
    @Column(length = 500)
    private String diagnosis;

    /** Текст заключения */
    @NotNull
    @Column(columnDefinition = "TEXT")
    private String conclusionText;

    /** Дата заключения */
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate conclusionDate;

    /** Образец, к которому относится заключение */
    @ManyToOne
    @JoinColumn(name = "sample_id", nullable = false)
    @ToString.Exclude
    private Sample sample;

    /** Врач-гистолог, составивший заключение */
    @ManyToOne
    @JoinColumn(name = "histologist_id", nullable = false)
    @ToString.Exclude
    private Employee histologist;
}
