"""
Оркестратор моделей улучшения изображений.

Сервис поддерживает три модели:
- baseline   — пайплайн на Pillow без нейросети (всегда доступен);
- esrgan     — Real-ESRGAN_x4plus с предобученными весами,
               делает 4× апскейл и заметное визуальное улучшение;
- unet       — наша собственная denoising U-Net, обучаемая локально
               на гистологических изображениях с искусственной деградацией.

Семантика mode для /enhance:
    auto      — ESRGAN, если веса доступны; иначе U-Net, иначе baseline.
    esrgan    — принудительно Real-ESRGAN.
    neural    — алиас esrgan (обратная совместимость со старым Java-клиентом).
    unet      — принудительно наш U-Net (если он обучен).
    baseline  — Pillow-пайплайн.
"""
from __future__ import annotations

import io
import json
import logging
import os
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps, UnidentifiedImageError

from .unet import (
    DEFAULT_IMAGE_SIZE,
    HistologyDenoisingUNet,
    HistologyImageDataset,
    torch_available,
)

try:
    import torch
    import torch.nn as nn
    import torch.nn.functional as functional
    from torch.utils.data import DataLoader, random_split
except ImportError:  # pragma: no cover
    torch = None
    nn = None
    functional = None
    DataLoader = None
    random_split = None

from .rrdbnet import RRDBNet
from .tiled_inference import tile_process

_log = logging.getLogger("uvicorn.error")


ROOT_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT_DIR / "data"
WEIGHTS_DIR = ROOT_DIR / "weights"
PRETRAINED_DIR = WEIGHTS_DIR / "pretrained"
TRAIN_SCRIPT_PATH = ROOT_DIR / "train.py"
WEIGHTS_DIR.mkdir(parents=True, exist_ok=True)
PRETRAINED_DIR.mkdir(parents=True, exist_ok=True)
DATA_DIR.mkdir(parents=True, exist_ok=True)

# U-Net (наша модель) — пути
METADATA_PATH = WEIGHTS_DIR / "latest_metadata.json"
WEIGHTS_PATH = WEIGHTS_DIR / "latest_autoencoder.pt"
HISTORY_PATH = WEIGHTS_DIR / "training_history.json"
STATUS_PATH = WEIGHTS_DIR / "training_status.json"
TRAINING_LOG_PATH = ROOT_DIR / "training.log"

# Real-ESRGAN — путь
ESRGAN_WEIGHTS_PATH = PRETRAINED_DIR / "RealESRGAN_x4plus.pth"

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".tif", ".tiff"}

UNET_MODEL_NAME = "histology-denoising-unet"
ESRGAN_MODEL_NAME = "RealESRGAN_x4plus"
BASELINE_MODEL_NAME = "baseline-pillow-enhancer"

# Параметры tile-инференса для CPU: больше тайл = меньше швов,
# но больше памяти. 400 — типичное значение Real-ESRGAN на CPU.
ESRGAN_TILE_SIZE = 400
ESRGAN_TILE_PAD = 10
ESRGAN_SCALE = 4


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
    """Сервис улучшения изображений с поддержкой трёх моделей."""

    default_model_name = UNET_MODEL_NAME

    def __init__(self) -> None:
        self.device = self._resolve_device()
        self.unet_model = self._load_unet_if_available()
        self.esrgan_model = self._load_esrgan_if_available()
        self.metadata = self._load_metadata()
        self.training_history = self._load_history()
        self.training_process: subprocess.Popen | None = None
        self.training_status = self._normalize_training_status(self._load_status())

    # ------------------------------------------------------------------
    # /models — список моделей
    # ------------------------------------------------------------------

    def list_models(self) -> list[dict[str, object]]:
        self.metadata = self._load_metadata()
        # ESRGAN — активная по умолчанию, если веса есть
        esrgan_active = self.esrgan_model is not None
        # UNet — активный, если есть обученные веса
        unet_active = self.unet_model is not None and not esrgan_active
        # baseline — активный, если ничего другого нет
        baseline_active = self.esrgan_model is None and self.unet_model is None

        models = [
            self._to_dict(
                ModelInfo(
                    model_name=BASELINE_MODEL_NAME,
                    description="Базовый пайплайн улучшения изображения на Pillow",
                    trained_date="2026-04-04",
                    epochs=0,
                    loss=0.0,
                    validation_loss=0.0,
                    active=baseline_active,
                )
            ),
            self._to_dict(
                ModelInfo(
                    model_name=ESRGAN_MODEL_NAME,
                    description=(
                        "Real-ESRGAN x4plus. Промышленная state-of-the-art модель "
                        "со скачанными весами. Делает 4× апскейл и восстановление деталей "
                        "за счёт RRDB-генератора, обученного с perceptual + adversarial loss."
                    ),
                    trained_date="2021-09-01" if esrgan_active else "—",
                    epochs=0,
                    loss=0.0,
                    validation_loss=0.0,
                    active=esrgan_active,
                )
            ),
        ]

        if self.metadata is not None:
            models.append(
                self._to_dict(
                    ModelInfo(
                        model_name=self.metadata.get("modelName", UNET_MODEL_NAME),
                        description=self.metadata.get(
                            "description",
                            "Denoising U-Net для восстановления искусственно ухудшенных гистологических изображений",
                        ),
                        trained_date=self.metadata.get("trainedDate", "—"),
                        epochs=int(self.metadata.get("epochs", 0)),
                        loss=float(self.metadata.get("loss", 0.0)),
                        validation_loss=float(self.metadata.get("validationLoss", 0.0)),
                        active=unet_active,
                    )
                )
            )
        else:
            models.append(
                self._to_dict(
                    ModelInfo(
                        model_name=UNET_MODEL_NAME,
                        description="Denoising U-Net. Будет активирован после обучения и сохранения весов.",
                        trained_date="—",
                        epochs=0,
                        loss=0.0,
                        validation_loss=0.0,
                        active=False,
                    )
                )
            )

        return models

    # ------------------------------------------------------------------
    # Обучение (только U-Net)
    # ------------------------------------------------------------------

    def train_model(
        self,
        epochs: int,
        batch_size: int,
        learning_rate: float,
        image_size: int = DEFAULT_IMAGE_SIZE,
    ) -> dict[str, object]:
        started_at = datetime.now()
        self._set_training_status(
            {
                "status": "running",
                "message": "Идёт обучение модели",
                "pid": os.getpid(),
                "startedAt": self._format_datetime(started_at),
                "epochs": epochs,
                "batchSize": batch_size,
                "learningRate": learning_rate,
                "imageSize": image_size,
                "datasetSize": len(self._discover_training_images()),
            }
        )

        if not torch_available():
            result = {
                "status": "error",
                "message": "Torch не установлен. Обучение нейросетевой модели недоступно.",
            }
            self._finish_training(result, started_at)
            return result

        image_paths = self._discover_training_images()
        if len(image_paths) < 10:
            result = {
                "status": "error",
                "message": (
                    "Для denoising-обучения нужно больше изображений в autoencoder/data. "
                    "Сейчас найдено недостаточно файлов."
                ),
                "datasetSize": len(image_paths),
            }
            self._finish_training(result, started_at)
            return result

        dataset = HistologyImageDataset(image_paths=image_paths, image_size=image_size)
        validation_size = max(2, int(len(dataset) * 0.2))
        train_size = len(dataset) - validation_size
        if train_size <= 0:
            result = {
                "status": "error",
                "message": "Недостаточно данных для разделения на train/validation.",
                "datasetSize": len(dataset),
            }
            self._finish_training(result, started_at)
            return result

        train_dataset, validation_dataset = random_split(dataset, [train_size, validation_size])
        train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
        validation_loader = DataLoader(validation_dataset, batch_size=batch_size, shuffle=False)

        model = HistologyDenoisingUNet().to(self.device)
        optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
        pixel_loss = nn.L1Loss()

        final_train_loss = 0.0
        final_validation_loss = 0.0
        final_psnr = 0.0
        final_ssim = 0.0

        for epoch_idx in range(epochs):
            model.train()
            train_losses = []
            for degraded_batch, clean_batch in train_loader:
                degraded_batch = degraded_batch.to(self.device)
                clean_batch = clean_batch.to(self.device)

                optimizer.zero_grad()
                restored = model(degraded_batch)

                content_loss = pixel_loss(restored, clean_batch)
                edge_loss = pixel_loss(
                    restored[:, :, 1:, 1:] - restored[:, :, :-1, :-1],
                    clean_batch[:, :, 1:, 1:] - clean_batch[:, :, :-1, :-1],
                )
                loss = content_loss + (0.15 * edge_loss)
                loss.backward()
                optimizer.step()
                train_losses.append(float(loss.item()))

            model.eval()
            validation_losses = []
            with torch.no_grad():
                for degraded_batch, clean_batch in validation_loader:
                    degraded_batch = degraded_batch.to(self.device)
                    clean_batch = clean_batch.to(self.device)
                    restored = model(degraded_batch)

                    content_loss = pixel_loss(restored, clean_batch)
                    edge_loss = pixel_loss(
                        restored[:, :, 1:, 1:] - restored[:, :, :-1, :-1],
                        clean_batch[:, :, 1:, 1:] - clean_batch[:, :, :-1, :-1],
                    )
                    loss = content_loss + (0.15 * edge_loss)
                    validation_losses.append(float(loss.item()))
                    batch_psnr, batch_ssim = self._calculate_metrics(restored, clean_batch)
                    final_psnr += batch_psnr
                    final_ssim += batch_ssim

            final_train_loss = sum(train_losses) / max(1, len(train_losses))
            final_validation_loss = sum(validation_losses) / max(1, len(validation_losses))
            validation_batches = max(1, len(validation_losses))
            final_psnr = final_psnr / validation_batches
            final_ssim = final_ssim / validation_batches

            self._set_training_status({
                "status": "running",
                "message": f"Эпоха {epoch_idx + 1} / {epochs}",
                "pid": os.getpid(),
                "startedAt": self._format_datetime(started_at),
                "epochs": epochs,
                "currentEpoch": epoch_idx + 1,
                "currentLoss": round(final_train_loss, 6),
                "currentValLoss": round(final_validation_loss, 6),
                "batchSize": batch_size,
                "learningRate": learning_rate,
                "imageSize": image_size,
                "datasetSize": len(dataset),
            })

        tmp_weights = WEIGHTS_PATH.with_suffix(".tmp")
        torch.save(model.state_dict(), tmp_weights)
        os.replace(str(tmp_weights), str(WEIGHTS_PATH))
        finished_at = datetime.now()
        duration_seconds = round((finished_at - started_at).total_seconds(), 2)
        metadata = {
            "modelName": UNET_MODEL_NAME,
            "description": "Denoising U-Net для восстановления искусственно ухудшенных гистологических изображений",
            "trainedDate": self._format_datetime(finished_at),
            "epochs": epochs,
            "loss": round(final_train_loss, 6),
            "validationLoss": round(final_validation_loss, 6),
            "psnr": round(final_psnr, 4),
            "ssim": round(final_ssim, 6),
            "device": self.device,
            "datasetSize": len(dataset),
            "imageSize": image_size,
            "trainingDurationSeconds": duration_seconds,
            "weightsPath": str(WEIGHTS_PATH.name),
        }
        METADATA_PATH.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        self.unet_model = self._load_unet_if_available()
        self.metadata = self._load_metadata()

        result = {
            "status": "ok",
            "message": "Обучение denoising-модели завершено, веса сохранены.",
            **metadata,
        }
        self._finish_training(result, started_at, finished_at)
        return result

    def start_training_async(
        self,
        epochs: int,
        batch_size: int,
        learning_rate: float,
        image_size: int = DEFAULT_IMAGE_SIZE,
    ) -> dict[str, object]:
        current_status = self._normalize_training_status(self._load_status())
        if current_status.get("status") == "running":
            return {
                "status": "busy",
                "message": "Обучение уже запущено. Дождитесь завершения текущего процесса.",
                **current_status,
            }

        started_at = datetime.now()
        dataset_size = len(self._discover_training_images())
        accepted_status = {
            "status": "running",
            "message": "Обучение запущено в фоновом режиме",
            "startedAt": self._format_datetime(started_at),
            "epochs": epochs,
            "batchSize": batch_size,
            "learningRate": learning_rate,
            "imageSize": image_size,
            "datasetSize": dataset_size,
        }
        self._set_training_status(accepted_status)

        command = [
            sys.executable,
            str(TRAIN_SCRIPT_PATH),
            "--epochs",
            str(epochs),
            "--batch-size",
            str(batch_size),
            "--learning-rate",
            str(learning_rate),
            "--image-size",
            str(image_size),
        ]

        try:
            if sys.platform == "win32":
                creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP | 0x08000000
            else:
                creation_flags = 0
            child_env = {**os.environ, "PYTHONUTF8": "1"}
            log_file = open(TRAINING_LOG_PATH, "w", encoding="utf-8")
            self.training_process = subprocess.Popen(
                command,
                cwd=str(ROOT_DIR),
                stdin=subprocess.DEVNULL,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                creationflags=creation_flags,
                env=child_env,
            )
            accepted_status["pid"] = self.training_process.pid
            self._set_training_status(accepted_status)
        except OSError as exc:
            self._finish_training(
                {
                    "status": "error",
                    "message": f"Не удалось запустить фоновое обучение: {exc}",
                    "epochs": epochs,
                    "batchSize": batch_size,
                    "learningRate": learning_rate,
                    "imageSize": image_size,
                    "datasetSize": dataset_size,
                },
                started_at,
                datetime.now(),
            )
            return {
                "status": "error",
                "message": f"Не удалось запустить фоновое обучение: {exc}",
            }

        return {
            **accepted_status,
            "status": "accepted",
            "message": "Обучение поставлено в очередь и выполняется в фоне.",
        }

    def get_training_status(self) -> dict[str, object]:
        self.training_status = self._normalize_training_status(self._load_status())
        return self.training_status

    def reset_training_status(self) -> dict[str, object]:
        if self.training_process is not None and self.training_process.poll() is not None:
            self.training_process = None
        idle: dict[str, object] = {"status": "idle", "message": "Status reset manually."}
        self._set_training_status(idle)
        self.training_status = idle
        return idle

    def get_training_history(self) -> list[dict[str, object]]:
        self.training_history = self._load_history()
        return self.training_history

    def clear_training_history(self) -> dict[str, object]:
        self.training_history = []
        HISTORY_PATH.write_text("[]", encoding="utf-8")
        return {"status": "ok", "message": "История очищена"}

    def get_metrics(self) -> dict[str, object]:
        self.metadata = self._load_metadata()
        active_model = (
            ESRGAN_MODEL_NAME if self.esrgan_model is not None
            else UNET_MODEL_NAME if self.unet_model is not None
            else BASELINE_MODEL_NAME
        )

        if self.metadata is None:
            return {
                "status": "ok" if self.esrgan_model is not None else "empty",
                "message": (
                    "Активна Real-ESRGAN со скачанными весами. Собственный U-Net ещё не обучался."
                    if self.esrgan_model is not None
                    else "Модель ещё не обучалась"
                ),
                "activeModel": active_model,
            }

        return {
            "status": "ok",
            "activeModel": active_model,
            "trainedDate": self.metadata.get("trainedDate"),
            "epochs": self.metadata.get("epochs"),
            "loss": self.metadata.get("loss"),
            "validationLoss": self.metadata.get("validationLoss"),
            "psnr": self.metadata.get("psnr"),
            "ssim": self.metadata.get("ssim"),
            "datasetSize": self.metadata.get("datasetSize"),
            "imageSize": self.metadata.get("imageSize"),
            "trainingDurationSeconds": self.metadata.get("trainingDurationSeconds"),
            "device": self.metadata.get("device"),
            "mode": (
                "esrgan" if self.esrgan_model is not None
                else "neural" if self.unet_model is not None
                else "baseline"
            ),
        }

    # ------------------------------------------------------------------
    # /enhance — улучшение изображения
    # ------------------------------------------------------------------

    def enhance_image(
        self,
        filename: str,
        payload: bytes,
        content_type: str | None,
        mode: str = "auto",
    ) -> tuple[bytes, str, str]:
        # Перезагружаем модели на случай если веса появились между запросами
        self.unet_model = self._load_unet_if_available()
        if self.esrgan_model is None:
            self.esrgan_model = self._load_esrgan_if_available()

        try:
            source = Image.open(io.BytesIO(payload))
        except UnidentifiedImageError as exc:
            raise ValueError(f"Не удалось распознать изображение: {filename}") from exc

        normalized = ImageOps.exif_transpose(source).convert("RGB")
        selected_mode = mode.lower().strip()
        allowed = {"auto", "baseline", "neural", "esrgan", "unet"}
        if selected_mode not in allowed:
            raise ValueError(
                "Недопустимый режим улучшения. Используйте auto, esrgan, unet, neural или baseline."
            )

        output_format = self._resolve_format(content_type=content_type, filename=filename)

        # neural — обратная совместимость, отображается на esrgan
        if selected_mode == "neural":
            selected_mode = "esrgan" if self.esrgan_model is not None else "unet"

        if selected_mode == "baseline":
            enhanced = self._enhance_with_baseline(normalized)
            return (
                self._encode_image(enhanced, output_format),
                self._content_type_for(output_format),
                BASELINE_MODEL_NAME,
            )

        if selected_mode == "esrgan":
            if self.esrgan_model is None:
                raise ValueError(
                    "Real-ESRGAN недоступен: веса RealESRGAN_x4plus.pth не найдены в weights/pretrained."
                )
            enhanced = self._enhance_with_esrgan(normalized)
            return (
                self._encode_image(enhanced, output_format),
                self._content_type_for(output_format),
                ESRGAN_MODEL_NAME,
            )

        if selected_mode == "unet":
            if self.unet_model is None:
                raise ValueError(
                    "Наша denoising U-Net недоступна: веса latest_autoencoder.pt не найдены. "
                    "Сначала запустите обучение через POST /train."
                )
            enhanced = self._enhance_with_unet(normalized)
            return (
                self._encode_image(enhanced, output_format),
                self._content_type_for(output_format),
                UNET_MODEL_NAME,
            )

        # mode == "auto" — выбираем лучшее доступное
        if self.esrgan_model is not None:
            enhanced = self._enhance_with_esrgan(normalized)
            return (
                self._encode_image(enhanced, output_format),
                self._content_type_for(output_format),
                ESRGAN_MODEL_NAME,
            )
        if self.unet_model is not None:
            enhanced = self._enhance_with_unet(normalized)
            return (
                self._encode_image(enhanced, output_format),
                self._content_type_for(output_format),
                UNET_MODEL_NAME,
            )
        enhanced = self._enhance_with_baseline(normalized)
        return (
            self._encode_image(enhanced, output_format),
            self._content_type_for(output_format),
            BASELINE_MODEL_NAME,
        )

    # ------------------------------------------------------------------
    # Реализации улучшения
    # ------------------------------------------------------------------

    def _enhance_with_baseline(self, image: Image.Image) -> Image.Image:
        normalized = ImageOps.autocontrast(image)
        normalized = normalized.filter(ImageFilter.SHARPEN)
        normalized = ImageEnhance.Contrast(normalized).enhance(1.08)
        normalized = ImageEnhance.Sharpness(normalized).enhance(1.15)
        return normalized

    def _enhance_with_unet(self, image: Image.Image) -> Image.Image:
        """U-Net не меняет разрешение: возвращает изображение того же размера,
        что и вход (как было в первой версии сервиса)."""
        if self.unet_model is None or not torch_available():
            return self._enhance_with_baseline(image)

        original_width, original_height = image.size
        image_array = np.asarray(image, dtype=np.float32) / 255.0
        image_tensor = torch.from_numpy(image_array).permute(2, 0, 1).unsqueeze(0)

        padded_tensor, padding = self._pad_to_multiple(image_tensor, multiple=8)
        padded_tensor = padded_tensor.to(self.device)

        self.unet_model.eval()
        with torch.no_grad():
            restored = self.unet_model(padded_tensor)

        restored = restored.cpu()
        left_pad, right_pad, top_pad, bottom_pad = padding
        if right_pad > 0:
            restored = restored[:, :, :, :-right_pad]
        if bottom_pad > 0:
            restored = restored[:, :, :-bottom_pad, :]
        if left_pad > 0:
            restored = restored[:, :, :, left_pad:]
        if top_pad > 0:
            restored = restored[:, :, top_pad:, :]

        restored_array = restored.squeeze(0).permute(1, 2, 0).numpy()
        restored_array = np.clip(restored_array * 255.0, 0, 255).astype(np.uint8)
        restored_image = Image.fromarray(restored_array)

        if restored_image.size != (original_width, original_height):
            restored_image = restored_image.resize((original_width, original_height))

        return restored_image

    def _enhance_with_esrgan(self, image: Image.Image) -> Image.Image:
        """Real-ESRGAN x4plus: возвращает изображение в 4× разрешении исходного.

        Использует tile-based инференс, чтобы выдерживать большие микроскопические
        снимки на CPU. Размер тайла 400 пикселей с padding 10.
        """
        if self.esrgan_model is None or not torch_available():
            return self._enhance_with_baseline(image)

        image_array = np.asarray(image, dtype=np.float32) / 255.0
        image_tensor = torch.from_numpy(image_array).permute(2, 0, 1).unsqueeze(0)
        image_tensor = image_tensor.to(self.device)

        # RRDBNet работает с любым входом, но для стабильности padding не нужен
        # (PixelShuffle нечувствителен к размерам). Тем не менее, мы паддингуем
        # до кратности 4 для гарантии — это требование некоторых реализаций.
        padded_tensor, padding = self._pad_to_multiple(image_tensor, multiple=4)

        self.esrgan_model.eval()
        with torch.no_grad():
            restored = tile_process(
                self.esrgan_model,
                padded_tensor,
                scale=ESRGAN_SCALE,
                tile_size=ESRGAN_TILE_SIZE,
                tile_pad=ESRGAN_TILE_PAD,
            )

        restored = restored.cpu()
        # Снимаем padding пропорционально scale
        left_pad, right_pad, top_pad, bottom_pad = padding
        if right_pad > 0:
            restored = restored[:, :, :, : -right_pad * ESRGAN_SCALE]
        if bottom_pad > 0:
            restored = restored[:, :, : -bottom_pad * ESRGAN_SCALE, :]
        if left_pad > 0:
            restored = restored[:, :, :, left_pad * ESRGAN_SCALE:]
        if top_pad > 0:
            restored = restored[:, :, top_pad * ESRGAN_SCALE:, :]

        restored_array = restored.squeeze(0).permute(1, 2, 0).numpy()
        restored_array = np.clip(restored_array * 255.0, 0, 255).astype(np.uint8)
        esrgan_image = Image.fromarray(restored_array)
        return self._post_process_enhanced(esrgan_image)

    def _post_process_enhanced(self, image: Image.Image) -> Image.Image:
        """Косметический пост-процессинг поверх ESRGAN-результата.

        Real-ESRGAN x4plus сам по себе выдаёт визуально «нейтральный» результат:
        он не размытый, но и не «звонкий». Чтобы микроскопический снимок выглядел
        более выраженно (контрастно, чётко, с насыщенными красителями) —
        добавляем лёгкий пост-процессинг на Pillow поверх готового тензора.

        Все коэффициенты подобраны эмпирически так, чтобы не «выжечь» изображение
        и не дорисовать ложных структур:
          - UnsharpMask: усиление высокочастотных деталей через свёртку с разницей
            оригинала и его размытой копии. radius=1.5, percent=130, threshold=3.
          - Contrast(1.10) — глобальный контраст, делает фон чище.
          - Color(1.15) — насыщенность цвета, делает гематоксилин/эозин ярче.
          - Sharpness(1.10) — финальный лёгкий sharpen.

        Все этапы коммутативны по входу/выходу и не меняют разрешение.
        """
        sharpened = image.filter(ImageFilter.UnsharpMask(radius=1.5, percent=130, threshold=3))
        contrasted = ImageEnhance.Contrast(sharpened).enhance(1.10)
        saturated = ImageEnhance.Color(contrasted).enhance(1.15)
        final = ImageEnhance.Sharpness(saturated).enhance(1.10)
        return final

    # ------------------------------------------------------------------
    # Загрузка моделей
    # ------------------------------------------------------------------

    def _load_unet_if_available(self):
        if not torch_available():
            return None
        if not WEIGHTS_PATH.exists():
            return None

        model = HistologyDenoisingUNet().to(self.device)
        state_dict = torch.load(WEIGHTS_PATH, map_location=self.device)
        model.load_state_dict(state_dict)
        model.eval()
        return model

    def _load_esrgan_if_available(self):
        if not torch_available():
            return None
        if not ESRGAN_WEIGHTS_PATH.exists():
            _log.info("[ESRGAN] Веса %s не найдены — Real-ESRGAN недоступен.", ESRGAN_WEIGHTS_PATH)
            return None
        try:
            model = RRDBNet(
                num_in_ch=3,
                num_out_ch=3,
                num_feat=64,
                num_block=23,
                num_grow_ch=32,
                scale=ESRGAN_SCALE,
            ).to(self.device)
            state_dict = torch.load(ESRGAN_WEIGHTS_PATH, map_location=self.device)
            # Веса Real-ESRGAN могут быть упакованы как {"params_ema": ...} или {"params": ...}
            if isinstance(state_dict, dict):
                if "params_ema" in state_dict:
                    state_dict = state_dict["params_ema"]
                elif "params" in state_dict:
                    state_dict = state_dict["params"]
            model.load_state_dict(state_dict, strict=True)
            model.eval()
            _log.info("[ESRGAN] Веса RealESRGAN_x4plus загружены успешно (device=%s).", self.device)
            return model
        except Exception as exc:  # pragma: no cover
            _log.exception("[ESRGAN] Не удалось загрузить веса: %s", exc)
            return None

    # ------------------------------------------------------------------
    # Утилиты
    # ------------------------------------------------------------------

    def _pad_to_multiple(self, tensor: torch.Tensor, multiple: int) -> tuple[torch.Tensor, tuple[int, int, int, int]]:
        _, _, height, width = tensor.shape
        pad_height = (multiple - (height % multiple)) % multiple
        pad_width = (multiple - (width % multiple)) % multiple

        top_pad = 0
        left_pad = 0
        bottom_pad = pad_height
        right_pad = pad_width

        padded = functional.pad(tensor, (left_pad, right_pad, top_pad, bottom_pad), mode="reflect")
        return padded, (left_pad, right_pad, top_pad, bottom_pad)

    def _encode_image(self, image: Image.Image, output_format: str) -> bytes:
        buffer = io.BytesIO()
        save_kwargs = {"format": output_format}
        if output_format == "JPEG":
            save_kwargs["quality"] = 95
        image.save(buffer, **save_kwargs)
        return buffer.getvalue()

    def _content_type_for(self, output_format: str) -> str:
        return "image/jpeg" if output_format == "JPEG" else "image/png"

    def _resolve_format(self, content_type: str | None, filename: str) -> str:
        if content_type == "image/png":
            return "PNG"
        lowered = filename.lower()
        if lowered.endswith(".png"):
            return "PNG"
        return "JPEG"

    def _resolve_device(self) -> str:
        if not torch_available():
            return "cpu"
        if torch.cuda.is_available():
            return "cuda"
        return "cpu"

    def _load_metadata(self) -> dict[str, object] | None:
        if not METADATA_PATH.exists():
            return None
        try:
            return json.loads(METADATA_PATH.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None

    def _load_history(self) -> list[dict[str, object]]:
        if not HISTORY_PATH.exists():
            return []
        try:
            data = json.loads(HISTORY_PATH.read_text(encoding="utf-8"))
            return data if isinstance(data, list) else []
        except (OSError, json.JSONDecodeError):
            return []

    def _load_status(self) -> dict[str, object]:
        default_status = {
            "status": "idle",
            "message": "Обучение не запущено",
        }
        if not STATUS_PATH.exists():
            return default_status
        try:
            data = json.loads(STATUS_PATH.read_text(encoding="utf-8"))
            return data if isinstance(data, dict) else default_status
        except (OSError, json.JSONDecodeError):
            return default_status

    def _set_training_status(self, status: dict[str, object]) -> None:
        self.training_status = status
        STATUS_PATH.write_text(json.dumps(status, ensure_ascii=False, indent=2), encoding="utf-8")

    def _normalize_training_status(self, status: dict[str, object]) -> dict[str, object]:
        if status.get("status") != "running":
            return status
        if self.training_process is not None:
            poll_result = self.training_process.poll()
            if poll_result is None:
                return status
        return {"status": "idle", "message": "Обучение не выполняется (процесс завершён)."}

    def _finish_training(
        self,
        result: dict[str, object],
        started_at: datetime,
        finished_at: datetime | None = None,
    ) -> None:
        finished_at = finished_at or datetime.now()
        duration_seconds = result.get("trainingDurationSeconds")
        if duration_seconds is None:
            duration_seconds = round((finished_at - started_at).total_seconds(), 2)
        finished = {
            **result,
            "startedAt": self._format_datetime(started_at),
            "finishedAt": self._format_datetime(finished_at),
            "trainingDurationSeconds": duration_seconds,
        }
        self.training_history.insert(0, finished)
        self.training_history = self.training_history[:20]
        HISTORY_PATH.write_text(
            json.dumps(self.training_history, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        self._set_training_status(finished)

    def _format_datetime(self, value: datetime) -> str:
        return value.strftime("%d.%m.%Y %H:%M")

    def _calculate_metrics(self, restored: torch.Tensor, clean: torch.Tensor) -> tuple[float, float]:
        mse = torch.mean((restored - clean) ** 2).item()
        psnr = 100.0 if mse == 0 else float(20.0 * np.log10(1.0 / np.sqrt(mse)))
        ssim = self._simple_ssim(restored, clean)
        return psnr, ssim

    def _simple_ssim(self, restored: torch.Tensor, clean: torch.Tensor) -> float:
        x = restored.detach().cpu().numpy().astype(np.float64)
        y = clean.detach().cpu().numpy().astype(np.float64)

        mu_x = x.mean()
        mu_y = y.mean()
        sigma_x = x.var()
        sigma_y = y.var()
        sigma_xy = ((x - mu_x) * (y - mu_y)).mean()

        c1 = 0.01 ** 2
        c2 = 0.03 ** 2
        numerator = (2 * mu_x * mu_y + c1) * (2 * sigma_xy + c2)
        denominator = (mu_x ** 2 + mu_y ** 2 + c1) * (sigma_x + sigma_y + c2)
        if denominator == 0:
            return 1.0
        return float(numerator / denominator)

    def _discover_training_images(self) -> list[Path]:
        return sorted(
            path
            for path in DATA_DIR.rglob("*")
            if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
        )

    def _to_dict(self, model: ModelInfo) -> dict[str, object]:
        return {
            "modelName": model.model_name,
            "description": model.description,
            "trainedDate": model.trained_date,
            "epochs": model.epochs,
            "loss": model.loss,
            "validationLoss": model.validation_loss,
            "active": model.active,
        }
