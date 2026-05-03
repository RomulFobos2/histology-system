package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum EnhancementQuality {
    BAD("Плохо"),
    GOOD("Хорошо"),
    EXCELLENT("Отлично");

    private final String displayName;

    EnhancementQuality(String displayName) {
        this.displayName = displayName;
    }
}
