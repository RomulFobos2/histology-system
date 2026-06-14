package ru.mai.histology.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mai.histology.enumeration.ActionType;
import ru.mai.histology.models.ActionLog;

import java.time.LocalDateTime;
import java.util.List;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {

    @Query("SELECT a FROM ActionLog a LEFT JOIN a.employee e " +
            "WHERE (:employeeId IS NULL OR e.id = :employeeId) " +
            "AND (:actionType IS NULL OR a.actionType = :actionType) " +
            "AND (:from IS NULL OR a.timestamp >= :from) " +
            "AND (:to IS NULL OR a.timestamp < :to) " +
            "ORDER BY a.timestamp DESC")
    Page<ActionLog> findByFilters(@Param("employeeId") Long employeeId,
                                   @Param("actionType") ActionType actionType,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    @Query("SELECT a FROM ActionLog a LEFT JOIN a.employee e " +
            "WHERE (:employeeId IS NULL OR e.id = :employeeId) " +
            "AND (:actionType IS NULL OR a.actionType = :actionType) " +
            "AND (:from IS NULL OR a.timestamp >= :from) " +
            "AND (:to IS NULL OR a.timestamp < :to) " +
            "ORDER BY a.timestamp DESC")
    List<ActionLog> findAllByFilters(@Param("employeeId") Long employeeId,
                                      @Param("actionType") ActionType actionType,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);
}
