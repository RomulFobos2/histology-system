package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Протокол гистологического исследования */
@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_researchProtocol")
public class ResearchProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер протокола (уникальный) */
    @NotNull
    @Column(length = 50, unique = true)
    private String protocolNumber;

    /** Дата создания */
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate createdDate;

    /** Текст протокола */
    @NotNull
    @Column(columnDefinition = "TEXT")
    private String protocolText;

    /** Образец, к которому относится протокол */
    @ManyToOne
    @JoinColumn(name = "sample_id", nullable = false)
    @ToString.Exclude
    private Sample sample;

    /** Сотрудник, создавший протокол */
    @ManyToOne
    @JoinColumn(name = "created_by_id", nullable = false)
    @ToString.Exclude
    private Employee createdBy;
}
