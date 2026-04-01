package ru.mai.histology.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeDTO {
    private Long id;
    private String lastName;
    private String firstName;
    private String middleName;
    private String fullName;
    private LocalDate birthDate;
    private String position;
    private String username;
    private String roleName;
    private String roleDescription;
    private boolean active;
    private boolean needChangePassword;
}
