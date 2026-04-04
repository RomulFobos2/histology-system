package ru.mai.histology.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mai.histology.models.AutoencoderModel;

import java.util.Optional;

public interface AutoencoderModelRepository extends JpaRepository<AutoencoderModel, Long> {

    Optional<AutoencoderModel> findByModelName(String modelName);

    Optional<AutoencoderModel> findFirstByIsActiveTrue();
}
