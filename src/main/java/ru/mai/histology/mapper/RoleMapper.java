package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import ru.mai.voshod.pneumotraining.dto.RoleDTO;
import ru.mai.voshod.pneumotraining.models.Role;

import java.util.List;

@Mapper
public interface RoleMapper {
    RoleMapper INSTANCE = Mappers.getMapper(RoleMapper.class);

    RoleDTO toDTO(Role role);

    List<RoleDTO> toDTOList(List<Role> roles);
}
