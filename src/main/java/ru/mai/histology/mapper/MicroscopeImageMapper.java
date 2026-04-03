package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.MicroscopeImageDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.MicroscopeImage;

import java.util.List;

@Mapper
public interface MicroscopeImageMapper {
    MicroscopeImageMapper INSTANCE = Mappers.getMapper(MicroscopeImageMapper.class);

    @Mapping(source = "sample.id", target = "sampleId")
    @Mapping(source = "sample.sampleNumber", target = "sampleNumber")
    @Mapping(source = "sample.forensicCase.id", target = "caseId")
    @Mapping(source = "sample.forensicCase.caseNumber", target = "caseNumber")
    @Mapping(source = "uploadedBy", target = "uploadedByFullName", qualifiedByName = "employeeFullName")
    @Mapping(source = "originalImage.id", target = "originalImageId")
    @Mapping(source = "image", target = "fileSizeFormatted", qualifiedByName = "formatFileSize")
    @Mapping(source = "enhanced", target = "enhanced")
    MicroscopeImageDTO toDTO(MicroscopeImage image);

    List<MicroscopeImageDTO> toDTOList(List<MicroscopeImage> images);

    @Named("employeeFullName")
    default String getEmployeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }

    @Named("formatFileSize")
    default String formatFileSize(MicroscopeImage image) {
        if (image.getFileSize() == null) return "—";
        long bytes = image.getFileSize();
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
        return String.format("%.1f МБ", bytes / (1024.0 * 1024.0));
    }
}
