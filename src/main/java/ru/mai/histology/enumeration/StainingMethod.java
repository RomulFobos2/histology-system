package ru.mai.histology.enumeration;

import lombok.Getter;

@Getter
public enum StainingMethod {
    HEMATOXYLIN_EOSIN("Гематоксилин-эозин"),
    VAN_GIESON("Ван Гизон"),
    PAS("ШИК-реакция"),
    MASSON_TRICHROME("Трихром Массона"),
    SUDAN("Судан III/IV"),
    NISSL("Ниссль"),
    GRAM("Грам"),
    ZIEHL_NEELSEN("Циль-Нильсен"),
    IMMUNOHISTOCHEMISTRY("Иммуногистохимия"),
    OTHER("Другое");

    private final String displayName;

    StainingMethod(String displayName) {
        this.displayName = displayName;
    }
}
