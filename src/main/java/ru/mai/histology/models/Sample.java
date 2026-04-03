package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import ru.mai.histology.enumeration.ResearchStage;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_sample", uniqueConstraints = @UniqueConstraint(columnNames = {"forensic_case_id", "sampleNumber"}))
public class Sample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(length = 50)
    private String sampleNumber;

    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate receiptDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TissueType tissueType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StainingMethod stainingMethod;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ResearchStage researchStage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SampleStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne
    @JoinColumn(name = "forensic_case_id", nullable = false)
    @ToString.Exclude
    private ForensicCase forensicCase;

    @ManyToOne
    @JoinColumn(name = "registered_by_id")
    @ToString.Exclude
    private Employee registeredBy;

    @ManyToOne
    @JoinColumn(name = "assigned_histologist_id")
    @ToString.Exclude
    private Employee assignedHistologist;
}
