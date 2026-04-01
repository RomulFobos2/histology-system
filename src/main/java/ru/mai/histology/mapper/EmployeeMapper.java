package ru.mai.histology.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import ru.mai.histology.dto.EmployeeDTO;
import ru.mai.histology.models.Employee;

import java.util.List;

@Mapper
public interface EmployeeMapper {
    EmployeeMapper INSTANCE = Mappers.getMapper(EmployeeMapper.class);

    @Mapping(source = "role.name", target = "roleName")
    @Mapping(source = "role.description", target = "roleDescription")
    @Mapping(source = "enabled", target = "active")
    @Mapping(source = "employee", target = "fullName", qualifiedByName = "fullNameMapper")
    @Mapping(source = "needChangePassword", target = "needChangePassword")
    EmployeeDTO toDTO(Employee employee);

    List<EmployeeDTO> toDTOList(List<Employee> employees);

    @Named("fullNameMapper")
    default String getFullName(Employee employee) {
        return employee.getLastName() + " " + employee.getFirstName() +
                (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty() ? " " + employee.getMiddleName() : "");
    }
}
