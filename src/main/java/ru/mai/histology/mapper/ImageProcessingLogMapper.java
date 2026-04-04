package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.ImageProcessingLogDTO;
import ru.mai.histology.models.Employee;
import ru.mai.histology.models.ImageProcessingLog;

import java.util.List;

@Mapper
public interface ImageProcessingLogMapper {
    ImageProcessingLogMapper INSTANCE = Mappers.getMapper(ImageProcessingLogMapper.class);

    @Mapping(source = "originalImage.id", target = "originalImageId")
    @Mapping(source = "enhancedImage.id", target = "enhancedImageId")
    @Mapping(source = "autoencoderModel.id", target = "autoencoderModelId")
    @Mapping(source = "autoencoderModel.modelName", target = "autoencoderModelName")
    @Mapping(source = "processedBy.id", target = "processedById")
    @Mapping(source = "processedBy", target = "processedByFullName", qualifiedByName = "employeeFullName")
    ImageProcessingLogDTO toDTO(ImageProcessingLog imageProcessingLog);

    List<ImageProcessingLogDTO> toDTOList(List<ImageProcessingLog> imageProcessingLogs);

    @Named("employeeFullName")
    default String employeeFullName(Employee employee) {
        if (employee == null) return null;
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()
                        ? " " + employee.getMiddleName() : "");
    }
}
