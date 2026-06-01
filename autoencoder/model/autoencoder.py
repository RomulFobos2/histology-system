"""
Обратная совместимость: историческое имя модуля `model.autoencoder`.

С момента добавления Real-ESRGAN внутреннее содержимое разнесено
по специализированным модулям, но публичные точки входа сохранены,
чтобы существующие импорты (`from model.autoencoder import AutoencoderService`)
в app.py и train.py продолжали работать без изменений.
"""
from .service import (  # noqa: F401
    AutoencoderService,
    BASELINE_MODEL_NAME,
    ESRGAN_MODEL_NAME,
    UNET_MODEL_NAME,
)
from .unet import (  # noqa: F401
    DEFAULT_IMAGE_SIZE,
    HistologyDenoisingUNet,
    HistologyImageDataset,
    torch_available,
)

# Старое имя для обратной совместимости
DEFAULT_MODEL_NAME = UNET_MODEL_NAME
