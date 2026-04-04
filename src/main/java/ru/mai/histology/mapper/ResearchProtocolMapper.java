package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.ResearchProtocolDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ResearchProtocol;

import java.util.List;

@Mapper
public interface ResearchProtocolMapper {
    ResearchProtocolMapper INSTANCE = Mappers.getMapper(ResearchProtocolMapper.class);

    @Mapping(source = "sample.id", target = "sampleId")
    @Mapping(source = "sample.sampleNumber", target = "sampleNumber")
    @Mapping(source = "sample.forensicCase.id", target = "caseId")
    @Mapping(source = "sample.forensicCase.caseNumber", target = "caseNumber")
    @Mapping(source = "sample.tissueType.displayName", target = "tissueTypeDisplayName")
    @Mapping(source = "sample.stainingMethod.displayName", target = "stainingMethodDisplayName")
    @Mapping(source = "createdBy.id", target = "createdById")
    @Mapping(source = "createdBy", target = "createdByFullName", qualifiedByName = "employeeFullName")
    ResearchProtocolDTO toDTO(ResearchProtocol protocol);

    List<ResearchProtocolDTO> toDTOList(List<ResearchProtocol> protocols);

    @Named("employeeFullName")
    default String getEmployeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }
}
