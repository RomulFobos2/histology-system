package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.AutoencoderModelDTO;
import ru.mai.histology.models.AutoencoderModel;

import java.util.List;

@Mapper
public interface AutoencoderModelMapper {
    AutoencoderModelMapper INSTANCE = Mappers.getMapper(AutoencoderModelMapper.class);

    AutoencoderModelDTO toDTO(AutoencoderModel autoencoderModel);

    List<AutoencoderModelDTO> toDTOList(List<AutoencoderModel> autoencoderModels);
}
