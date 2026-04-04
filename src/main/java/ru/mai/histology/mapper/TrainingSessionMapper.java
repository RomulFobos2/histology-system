package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.TrainingSessionDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.TrainingSession;

import java.util.List;

@Mapper
public interface TrainingSessionMapper {
    TrainingSessionMapper INSTANCE = Mappers.getMapper(TrainingSessionMapper.class);

    @Mapping(source = "triggeredBy.id", target = "triggeredById")
    @Mapping(source = "triggeredBy", target = "triggeredByFullName", qualifiedByName = "employeeFullName")
    TrainingSessionDTO toDTO(TrainingSession trainingSession);

    List<TrainingSessionDTO> toDTOList(List<TrainingSession> trainingSessions);

    @Named("employeeFullName")
    default String employeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }
}
