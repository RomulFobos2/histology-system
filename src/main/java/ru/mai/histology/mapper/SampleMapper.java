package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.Sample;

import java.util.List;

@Mapper
public interface SampleMapper {
    SampleMapper INSTANCE = Mappers.getMapper(SampleMapper.class);

    @Mapping(source = "tissueType.displayName", target = "tissueTypeDisplayName")
    @Mapping(source = "stainingMethod.displayName", target = "stainingMethodDisplayName")
    @Mapping(source = "status.displayName", target = "statusDisplayName")
    @Mapping(source = "forensicCase.id", target = "caseId")
    @Mapping(source = "forensicCase.caseNumber", target = "caseNumber")
    @Mapping(source = "registeredBy", target = "registeredByFullName", qualifiedByName = "employeeFullName")
    @Mapping(source = "assignedHistologist.id", target = "assignedHistologistId")
    @Mapping(source = "assignedHistologist", target = "assignedHistologistFullName", qualifiedByName = "employeeFullName")
    SampleDTO toDTO(Sample sample);

    List<SampleDTO> toDTOList(List<Sample> samples);

    @Named("employeeFullName")
    default String getEmployeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty() ? " " + employee.getMiddleName() : "");
    }
}
