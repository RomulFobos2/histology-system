package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum SampleStatus {
    NEW("Новый"),
    IN_PROGRESS("В работе"),
    AWAITING_ANALYSIS("Ожидает анализа"),
    ANALYZED("Проанализирован"),
    CONCLUDED("Заключение выдано"),
    ARCHIVED("Архивирован");

    private final String displayName;

    SampleStatus(String displayName) {
        this.displayName = displayName;
    }
}
