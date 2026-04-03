package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import ru.mai.histology.enumeration.CaseStatus;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_forensicCase")
public class ForensicCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true, length = 50)
    private String caseNumber;

    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate receiptDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CaseStatus status;

    @ManyToOne
    @JoinColumn(name = "responsible_expert_id")
    @ToString.Exclude
    private Employee responsibleExpert;

    @OneToMany(mappedBy = "forensicCase")
    @ToString.Exclude
    private List<Sample> samples;
}
