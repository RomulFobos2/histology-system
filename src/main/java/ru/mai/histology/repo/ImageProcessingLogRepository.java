package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.ImageProcessingLog;

import java.time.LocalDate;
import java.util.List;

public interface ImageProcessingLogRepository extends JpaRepository<ImageProcessingLog, Long> {

    List<ImageProcessingLog> findAllByProcessedDateBetweenOrderByProcessedDateDesc(LocalDate from, LocalDate to);
}
