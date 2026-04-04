from __future__ import annotations

import io
from dataclasses import dataclass

from PIL import Image, ImageEnhance, ImageFilter, ImageOps, UnidentifiedImageError


@dataclass(frozen=True)
class ModelInfo:
    model_name: str
    description: str
    trained_date: str
    epochs: int
    loss: float
    validation_loss: float
    active: bool


class AutoencoderService:
    """
    Базовая реализация сервиса улучшения изображения.

    Сейчас используется безопасный baseline-пайплайн на Pillow:
    autocontrast + лёгкое повышение резкости и контраста.
    Структура подготовлена так, чтобы позже заменить его на реальный
    свёрточный автоэнкодер без изменения REST API.
    """

    default_model_name = "baseline-pillow-enhancer"

    def __init__(self) -> None:
        self._models = [
            ModelInfo(
                model_name=self.default_model_name,
                description="Базовый пайплайн улучшения изображения на Pillow",
                trained_date="2026-04-04",
                epochs=0,
                loss=0.0,
                validation_loss=0.0,
                active=True,
            )
        ]

    def list_models(self) -> list[dict[str, object]]:
        return [
            {
                "modelName": model.model_name,
                "description": model.description,
                "trainedDate": model.trained_date,
                "epochs": model.epochs,
                "loss": model.loss,
                "validationLoss": model.validation_loss,
                "active": model.active,
            }
            for model in self._models
        ]

    def train_stub(self, epochs: int, batch_size: int, learning_rate: float) -> dict[str, object]:
        return {
            "status": "accepted",
            "message": "Тренировка реального автоэнкодера ещё не реализована. API и структура готовы для следующего шага.",
            "params": {
                "epochs": epochs,
                "batchSize": batch_size,
                "learningRate": learning_rate,
            },
        }

    def enhance_image(
        self,
        filename: str,
        payload: bytes,
        content_type: str | None,
    ) -> tuple[bytes, str]:
        try:
            source = Image.open(io.BytesIO(payload))
        except UnidentifiedImageError as exc:
            raise ValueError(f"Не удалось распознать изображение: {filename}") from exc

        normalized = ImageOps.exif_transpose(source).convert("RGB")
        normalized = ImageOps.autocontrast(normalized)
        normalized = normalized.filter(ImageFilter.SHARPEN)
        normalized = ImageEnhance.Contrast(normalized).enhance(1.08)
        normalized = ImageEnhance.Sharpness(normalized).enhance(1.15)

        buffer = io.BytesIO()
        output_format = self._resolve_format(content_type=content_type, filename=filename)
        normalized.save(buffer, format=output_format, quality=95)

        output_content_type = "image/jpeg" if output_format == "JPEG" else "image/png"
        return buffer.getvalue(), output_content_type

    def _resolve_format(self, content_type: str | None, filename: str) -> str:
        if content_type == "image/png":
            return "PNG"
        lowered = filename.lower()
        if lowered.endswith(".png"):
            return "PNG"
        return "JPEG"
