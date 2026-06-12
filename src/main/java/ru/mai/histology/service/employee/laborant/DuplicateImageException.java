package ru.mai.histology.service.employee.laborant;

import ru.mai.histology.models.MicroscopeImage;

public class DuplicateImageException extends RuntimeException {

    private final MicroscopeImage existing;

    public DuplicateImageException(MicroscopeImage existing) {
        super("Файл уже загружен в систему: imageId=" + existing.getId());
        this.existing = existing;
    }

    public MicroscopeImage getExisting() {
        return existing;
    }
}
