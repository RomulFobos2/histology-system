package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.ForensicConclusionDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ForensicConclusion;

import java.util.List;

@Mapper
public interface ForensicConclusionMapper {
    ForensicConclusionMapper INSTANCE = Mappers.getMapper(ForensicConclusionMapper.class);

    @Mapping(source = "sample.id", target = "sampleId")
    @Mapping(source = "sample.sampleNumber", target = "sampleNumber")
    @Mapping(source = "sample.forensicCase.id", target = "caseId")
    @Mapping(source = "sample.forensicCase.caseNumber", target = "caseNumber")
    @Mapping(source = "sample.tissueType.displayName", target = "tissueTypeDisplayName")
    @Mapping(source = "sample.stainingMethod.displayName", target = "stainingMethodDisplayName")
    @Mapping(source = "sample.status.displayName", target = "sampleStatusDisplayName")
    @Mapping(source = "head.id", target = "headId")
    @Mapping(source = "head", target = "headFullName", qualifiedByName = "employeeFullName")
    @Mapping(target = "histologistDiagnosis", ignore = true)
    @Mapping(target = "histologistConclusionText", ignore = true)
    @Mapping(target = "histologistFullName", ignore = true)
    ForensicConclusionDTO toDTO(ForensicConclusion conclusion);

    List<ForensicConclusionDTO> toDTOList(List<ForensicConclusion> conclusions);

    @Named("employeeFullName")
    default String getEmployeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }
}
