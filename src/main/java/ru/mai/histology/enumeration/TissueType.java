package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum TissueType {
    LIVER("Печень"),
    KIDNEY("Почка"),
    HEART("Сердце"),
    LUNG("Лёгкое"),
    BRAIN("Головной мозг"),
    SKIN("Кожа"),
    MUSCLE("Мышечная ткань"),
    BONE("Костная ткань"),
    SPLEEN("Селезёнка"),
    INTESTINE("Кишечник"),
    STOMACH("Желудок"),
    OTHER("Другое");

    private final String displayName;

    TissueType(String displayName) {
        this.displayName = displayName;
    }
}
