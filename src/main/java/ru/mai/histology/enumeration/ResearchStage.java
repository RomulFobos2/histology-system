package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum ResearchStage {
    RECEIVED("Принят"),
    FIXATION("Фиксация"),
    EMBEDDING("Заливка"),
    SECTIONING("Микротомия"),
    STAINING("Окрашивание"),
    MICROSCOPY("Микроскопия"),
    ANALYSIS("Анализ"),
    COMPLETED("Завершён");

    private final String displayName;

    ResearchStage(String displayName) {
        this.displayName = displayName;
    }
}
