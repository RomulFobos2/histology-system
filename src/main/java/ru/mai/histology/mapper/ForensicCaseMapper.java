package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.ForensicCaseDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ForensicCase;

import java.util.List;

@Mapper
public interface ForensicCaseMapper {
    ForensicCaseMapper INSTANCE = Mappers.getMapper(ForensicCaseMapper.class);

    @Mapping(source = "status.displayName", target = "statusDisplayName")
    @Mapping(source = "responsibleExpert.id", target = "expertId")
    @Mapping(source = "responsibleExpert", target = "expertFullName", qualifiedByName = "expertFullName")
    @Mapping(source = "forensicCase", target = "sampleCount", qualifiedByName = "sampleCount")
    ForensicCaseDTO toDTO(ForensicCase forensicCase);

    List<ForensicCaseDTO> toDTOList(List<ForensicCase> cases);

    @Named("expertFullName")
    default String getExpertFullName(Employee expert) {
        if (expert == null) return null;
        return expert.getLastName() + " " + expert.getFirstName() +
                (expert.getMiddleName() != null && !expert.getMiddleName().isEmpty() ? " " + expert.getMiddleName() : "");
    }

    @Named("sampleCount")
    default long getSampleCount(ForensicCase forensicCase) {
        if (forensicCase.getSamples() == null) return 0;
        return forensicCase.getSamples().size();
    }
}
