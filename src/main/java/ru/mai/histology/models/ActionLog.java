package ru.mai.histology.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import ru.mai.histology.enumeration.ActionType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "t_action_log", indexes = {
        @Index(name = "idx_action_log_timestamp", columnList = "timestamp"),
        @Index(name = "idx_action_log_employee", columnList = "employee_id"),
        @Index(name = "idx_action_log_type", columnList = "actionType")
})
public class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @ToString.Exclude
    private Employee employee;

    @Column(length = 100)
    private String usernameSnapshot;

    @Column(length = 255)
    private String fullNameSnapshot;

    @Column(length = 50)
    private String roleSnapshot;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private ActionType actionType;

    @Column(length = 50)
    private String entityType;

    private Long entityId;

    @Column(length = 500)
    private String description;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean success = true;
}
