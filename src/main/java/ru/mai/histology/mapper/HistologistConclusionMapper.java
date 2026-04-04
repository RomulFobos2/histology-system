package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.HistologistConclusionDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.HistologistConclusion;

import java.util.List;

@Mapper
public interface HistologistConclusionMapper {
    HistologistConclusionMapper INSTANCE = Mappers.getMapper(HistologistConclusionMapper.class);

    @Mapping(source = "sample.id", target = "sampleId")
    @Mapping(source = "sample.sampleNumber", target = "sampleNumber")
    @Mapping(source = "sample.forensicCase.id", target = "caseId")
    @Mapping(source = "sample.forensicCase.caseNumber", target = "caseNumber")
    @Mapping(source = "sample.tissueType.displayName", target = "tissueTypeDisplayName")
    @Mapping(source = "sample.stainingMethod.displayName", target = "stainingMethodDisplayName")
    @Mapping(source = "histologist.id", target = "histologistId")
    @Mapping(source = "histologist", target = "histologistFullName", qualifiedByName = "employeeFullName")
    HistologistConclusionDTO toDTO(HistologistConclusion conclusion);

    List<HistologistConclusionDTO> toDTOList(List<HistologistConclusion> conclusions);

    @Named("employeeFullName")
    default String getEmployeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }
}
