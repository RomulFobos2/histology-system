package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum CaseStatus {
    OPEN("Открыто"),
    IN_PROGRESS("В работе"),
    CONCLUDED("Заключение выдано"),
    CLOSED("Закрыто");

    private final String displayName;

    CaseStatus(String displayName) {
        this.displayName = displayName;
    }
}
