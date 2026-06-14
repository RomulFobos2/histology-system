package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum ActionType {
    LOGIN_SUCCESS("Вход в систему"),
    LOGIN_FAILURE("Неудачная попытка входа"),

    CASE_CREATED("Создано дело"),
    CASE_UPDATED("Изменено дело"),
    CASE_DELETED("Удалено дело"),

    IMAGE_UPLOADED("Загружено изображение"),
    IMAGE_DELETED("Удалено изображение"),
    IMAGE_ENHANCED("Улучшено изображение"),

    PROTOCOL_CREATED("Создан протокол"),
    PROTOCOL_UPDATED("Изменён протокол"),

    HISTOLOGIST_CONCLUSION_CREATED("Создано заключение гистолога"),
    HISTOLOGIST_CONCLUSION_UPDATED("Изменено заключение гистолога"),
    HISTOLOGIST_CONCLUSION_DELETED("Удалено заключение гистолога"),

    FORENSIC_CONCLUSION_CREATED("Создано судебно-медицинское заключение"),
    FORENSIC_CONCLUSION_UPDATED("Изменено судебно-медицинское заключение");

    private final String description;

    ActionType(String description) {
        this.description = description;
    }
}
