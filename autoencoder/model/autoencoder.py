from __future__ import annotations

import io
import json
import random
import os
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps, UnidentifiedImageError

try:
    import torch
    import torch.nn as nn
    import torch.nn.functional as functional
    from torch.utils.data import DataLoader, Dataset, random_split
except ImportError:  # pragma: no cover
    torch = None
    nn = None
    functional = None
    Dataset = object
    DataLoader = None
    random_split = None


ROOT_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT_DIR / "data"
WEIGHTS_DIR = ROOT_DIR / "weights"
TRAIN_SCRIPT_PATH = ROOT_DIR / "train.py"
WEIGHTS_DIR.mkdir(parents=True, exist_ok=True)
DATA_DIR.mkdir(parents=True, exist_ok=True)

METADATA_PATH = WEIGHTS_DIR / "latest_metadata.json"
WEIGHTS_PATH = WEIGHTS_DIR / "latest_autoencoder.pt"
HISTORY_PATH = WEIGHTS_DIR / "training_history.json"
STATUS_PATH = WEIGHTS_DIR / "training_status.json"

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".tif", ".tiff"}
DEFAULT_MODEL_NAME = "histology-denoising-unet"
BASELINE_MODEL_NAME = "baseline-pillow-enhancer"
DEFAULT_IMAGE_SIZE = 256


@dataclass(frozen=True)
class ModelInfo:
    model_name: str
    description: str
    trained_date: str
    epochs: int
    loss: float
    validation_loss: float
    active: bool


def torch_available() -> bool:
    return torch is not None and nn is not None and functional is not None


if torch_available():
    class DoubleConv(nn.Module):
        def __init__(self, in_channels: int, out_channels: int) -> None:
            super().__init__()
            self.layers = nn.Sequential(
                nn.Conv2d(in_channels, out_channels, kernel_size=3, padding=1),
                nn.BatchNorm2d(out_channels),
                nn.ReLU(inplace=True),
                nn.Conv2d(out_channels, out_channels, kernel_size=3, padding=1),
                nn.BatchNorm2d(out_channels),
                nn.ReLU(inplace=True),
            )

        def forward(self, inputs: torch.Tensor) -> torch.Tensor:
            return self.layers(inputs)


    class DownBlock(nn.Module):
        def __init__(self, in_channels: int, out_channels: int) -> None:
            super().__init__()
            self.conv = DoubleConv(in_channels, out_channels)
            self.pool = nn.MaxPool2d(2)

        def forward(self, inputs: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
            features = self.conv(inputs)
            return features, self.pool(features)


    class UpBlock(nn.Module):
        def __init__(self, in_channels: int, skip_channels: int, out_channels: int) -> None:
            super().__init__()
            self.up = nn.ConvTranspose2d(in_channels, out_channels, kernel_size=2, stride=2)
            self.conv = DoubleConv(out_channels + skip_channels, out_channels)

        def forward(self, inputs: torch.Tensor, skip: torch.Tensor) -> torch.Tensor:
            upsampled = self.up(inputs)
            if upsampled.shape[-2:] != skip.shape[-2:]:
                upsampled = functional.interpolate(
                    upsampled,
                    size=skip.shape[-2:],
                    mode="bilinear",
                    align_corners=False,
                )
            merged = torch.cat([upsampled, skip], dim=1)
            return self.conv(merged)


    class HistologyDenoisingUNet(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.down1 = DownBlock(3, 32)
            self.down2 = DownBlock(32, 64)
            self.down3 = DownBlock(64, 128)
            self.bottleneck = DoubleConv(128, 256)
            self.up3 = UpBlock(256, 128, 128)
            self.up2 = UpBlock(128, 64, 64)
            self.up1 = UpBlock(64, 32, 32)
            self.final = nn.Conv2d(32, 3, kernel_size=1)

        def forward(self, inputs: torch.Tensor) -> torch.Tensor:
            skip1, pooled1 = self.down1(inputs)
            skip2, pooled2 = self.down2(pooled1)
            skip3, pooled3 = self.down3(pooled2)

            bottleneck = self.bottleneck(pooled3)

            up3 = self.up3(bottleneck, skip3)
            up2 = self.up2(up3, skip2)
            up1 = self.up1(up2, skip1)

            residual = torch.sigmoid(self.final(up1))
            return torch.clamp((inputs * 0.35) + (residual * 0.65), 0.0, 1.0)


    class HistologyImageDataset(Dataset):
        def __init__(self, image_paths: list[Path], image_size: int = DEFAULT_IMAGE_SIZE) -> None:
            self.image_paths = image_paths
            self.image_size = image_size

        def __len__(self) -> int:
            return len(self.image_paths)

        def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor]:
            image_path = self.image_paths[index]
            with Image.open(image_path) as image:
                clean_image = ImageOps.exif_transpose(image).convert("RGB")

            clean_crop = self._prepare_crop(clean_image)
            degraded_crop = self._degrade_image(clean_crop.copy())

            clean_tensor = self._to_tensor(clean_crop)
            degraded_tensor = self._to_tensor(degraded_crop)
            return degraded_tensor, clean_tensor

        def _prepare_crop(self, image: Image.Image) -> Image.Image:
            width, height = image.size
            crop_size = self.image_size

            if width < crop_size or height < crop_size:
                image = image.resize((max(width, crop_size), max(height, crop_size)))
                width, height = image.size

            if width == crop_size and height == crop_size:
                crop = image
            else:
                left = random.randint(0, width - crop_size)
                top = random.randint(0, height - crop_size)
                crop = image.crop((left, top, left + crop_size, top + crop_size))

            if random.random() < 0.5:
                crop = ImageOps.mirror(crop)
            if random.random() < 0.5:
                crop = ImageOps.flip(crop)

            return crop

        def _degrade_image(self, image: Image.Image) -> Image.Image:
            width, height = image.size

            if random.random() < 0.9:
                scale = random.uniform(0.45, 0.85)
                resized = image.resize(
                    (max(32, int(width * scale)), max(32, int(height * scale))),
                    resample=Image.Resampling.BILINEAR,
                )
                image = resized.resize((width, height), resample=Image.Resampling.BILINEAR)

            if random.random() < 0.85:
                image = image.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.4, 1.8)))

            if random.random() < 0.8:
                image = ImageEnhance.Contrast(image).enhance(random.uniform(0.75, 1.15))

            if random.random() < 0.7:
                image = ImageEnhance.Brightness(image).enhance(random.uniform(0.85, 1.1))

            array = np.asarray(image, dtype=np.float32)
            if random.random() < 0.9:
                noise_sigma = random.uniform(4.0, 18.0)
                noise = np.random.normal(0.0, noise_sigma, array.shape)
                array = np.clip(array + noise, 0, 255)

            if random.random() < 0.6:
                jpeg_quality = random.randint(20, 60)
                buffer = io.BytesIO()
                Image.fromarray(array.astype(np.uint8)).save(buffer, format="JPEG", quality=jpeg_quality)
                buffer.seek(0)
                array = np.asarray(Image.open(buffer).convert("RGB"), dtype=np.float32)

            return Image.fromarray(array.astype(np.uint8))

        def _to_tensor(self, image: Image.Image) -> torch.Tensor:
            array = np.asarray(image, dtype=np.float32) / 255.0
            return torch.from_numpy(array).permute(2, 0, 1)


class AutoencoderService:
    """
    РЎРµСЂРІРёСЃ СѓР»СѓС‡С€РµРЅРёСЏ РёР·РѕР±СЂР°Р¶РµРЅРёСЏ.

    Р РµР¶РёРјС‹ СЂР°Р±РѕС‚С‹:
    - baseline: СѓР»СѓС‡С€РµРЅРёРµ РЅР° Pillow Р±РµР· РЅРµР№СЂРѕСЃРµС‚Рё
    - neural: СѓР»СѓС‡С€РµРЅРёРµ С‡РµСЂРµР· РѕР±СѓС‡РµРЅРЅС‹Р№ denoising U-Net
    - auto: РёСЃРїРѕР»СЊР·РѕРІР°С‚СЊ neural, РµСЃР»Рё РІРµСЃР° РґРѕСЃС‚СѓРїРЅС‹, РёРЅР°С‡Рµ baseline
    """

    default_model_name = DEFAULT_MODEL_NAME

    def __init__(self) -> None:
        self.device = self._resolve_device()
        self.model = self._load_model_if_available()
        self.metadata = self._load_metadata()
        self.training_history = self._load_history()
        self.training_status = self._normalize_training_status(self._load_status())
        self.training_process: subprocess.Popen | None = None

    def list_models(self) -> list[dict[str, object]]:
        self.model = self._load_model_if_available()
        self.metadata = self._load_metadata()
        models = [
            self._to_dict(
                ModelInfo(
                    model_name=BASELINE_MODEL_NAME,
                    description="Р‘Р°Р·РѕРІС‹Р№ РїР°Р№РїР»Р°Р№РЅ СѓР»СѓС‡С€РµРЅРёСЏ РёР·РѕР±СЂР°Р¶РµРЅРёСЏ РЅР° Pillow",
                    trained_date="2026-04-04",
                    epochs=0,
                    loss=0.0,
                    validation_loss=0.0,
                    active=self.model is None,
                )
            )
        ]

        if self.metadata is not None:
            models.append(
                self._to_dict(
                    ModelInfo(
                        model_name=self.metadata.get("modelName", DEFAULT_MODEL_NAME),
                        description=self.metadata.get(
                            "description",
                            "Denoising U-Net РґР»СЏ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ РёСЃРєСѓСЃСЃС‚РІРµРЅРЅРѕ СѓС…СѓРґС€РµРЅРЅС‹С… РіРёСЃС‚РѕР»РѕРіРёС‡РµСЃРєРёС… РёР·РѕР±СЂР°Р¶РµРЅРёР№",
                        ),
                        trained_date=self.metadata.get("trainedDate", "вЂ”"),
                        epochs=int(self.metadata.get("epochs", 0)),
                        loss=float(self.metadata.get("loss", 0.0)),
                        validation_loss=float(self.metadata.get("validationLoss", 0.0)),
                        active=self.model is not None,
                    )
                )
            )
        else:
            models.append(
                self._to_dict(
                    ModelInfo(
                        model_name=DEFAULT_MODEL_NAME,
                        description="Denoising U-Net. Р‘СѓРґРµС‚ Р°РєС‚РёРІРёСЂРѕРІР°РЅ РїРѕСЃР»Рµ РѕР±СѓС‡РµРЅРёСЏ Рё СЃРѕС…СЂР°РЅРµРЅРёСЏ РІРµСЃРѕРІ.",
                        trained_date="вЂ”",
                        epochs=0,
                        loss=0.0,
                        validation_loss=0.0,
                        active=False,
                    )
                )
            )

        return models

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
                "message": "РРґС‘С‚ РѕР±СѓС‡РµРЅРёРµ РјРѕРґРµР»Рё",
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
                "message": "Torch РЅРµ СѓСЃС‚Р°РЅРѕРІР»РµРЅ. РћР±СѓС‡РµРЅРёРµ РЅРµР№СЂРѕСЃРµС‚РµРІРѕР№ РјРѕРґРµР»Рё РЅРµРґРѕСЃС‚СѓРїРЅРѕ.",
            }
            self._finish_training(result, started_at)
            return result

        image_paths = self._discover_training_images()
        if len(image_paths) < 10:
            result = {
                "status": "error",
                "message": (
                    "Р”Р»СЏ denoising-РѕР±СѓС‡РµРЅРёСЏ РЅСѓР¶РЅРѕ Р±РѕР»СЊС€Рµ РёР·РѕР±СЂР°Р¶РµРЅРёР№ РІ autoencoder/data. "
                    "РЎРµР№С‡Р°СЃ РЅР°Р№РґРµРЅРѕ РЅРµРґРѕСЃС‚Р°С‚РѕС‡РЅРѕ С„Р°Р№Р»РѕРІ."
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
                "message": "РќРµРґРѕСЃС‚Р°С‚РѕС‡РЅРѕ РґР°РЅРЅС‹С… РґР»СЏ СЂР°Р·РґРµР»РµРЅРёСЏ РЅР° train/validation.",
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

        for _ in range(epochs):
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

        torch.save(model.state_dict(), WEIGHTS_PATH)
        finished_at = datetime.now()
        duration_seconds = round((finished_at - started_at).total_seconds(), 2)
        metadata = {
            "modelName": DEFAULT_MODEL_NAME,
            "description": "Denoising U-Net РґР»СЏ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ РёСЃРєСѓСЃСЃС‚РІРµРЅРЅРѕ СѓС…СѓРґС€РµРЅРЅС‹С… РіРёСЃС‚РѕР»РѕРіРёС‡РµСЃРєРёС… РёР·РѕР±СЂР°Р¶РµРЅРёР№",
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

        self.model = self._load_model_if_available()
        self.metadata = self._load_metadata()

        result = {
            "status": "ok",
            "message": "РћР±СѓС‡РµРЅРёРµ denoising-РјРѕРґРµР»Рё Р·Р°РІРµСЂС€РµРЅРѕ, РІРµСЃР° СЃРѕС…СЂР°РЅРµРЅС‹.",
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
        current_status = self._load_status()
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
            # На Windows: CREATE_NEW_PROCESS_GROUP изолирует дочерний процесс
            # от консольной группы uvicorn, чтобы завершение train.py
            # не отправляло CTRL_CLOSE_EVENT родителю и не гасило сервис.
            creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP if sys.platform == "win32" else 0
            self.training_process = subprocess.Popen(
                command,
                cwd=str(ROOT_DIR),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=creation_flags,
            )
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
        """Принудительно сбрасывает зависший статус обучения в idle."""
        if self.training_process is not None and self.training_process.poll() is not None:
            self.training_process = None
        idle: dict[str, object] = {"status": "idle", "message": "Статус сброшен вручную."}
        self._set_training_status(idle)
        self.training_status = idle
        return idle

    def get_training_history(self) -> list[dict[str, object]]:
        self.training_history = self._load_history()
        return self.training_history

    def get_metrics(self) -> dict[str, object]:
        self.model = self._load_model_if_available()
        self.metadata = self._load_metadata()
        if self.metadata is None:
            return {
                "status": "empty",
                "message": "РњРѕРґРµР»СЊ РµС‰С‘ РЅРµ РѕР±СѓС‡Р°Р»Р°СЃСЊ",
                "activeModel": BASELINE_MODEL_NAME,
            }

        return {
            "status": "ok",
            "activeModel": self.metadata.get("modelName", DEFAULT_MODEL_NAME),
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
            "mode": "neural" if self.model is not None else "baseline",
        }
    def enhance_image(
        self,
        filename: str,
        payload: bytes,
        content_type: str | None,
        mode: str = "auto",
    ) -> tuple[bytes, str, str]:
        self.model = self._load_model_if_available()
        try:
            source = Image.open(io.BytesIO(payload))
        except UnidentifiedImageError as exc:
            raise ValueError(f"РќРµ СѓРґР°Р»РѕСЃСЊ СЂР°СЃРїРѕР·РЅР°С‚СЊ РёР·РѕР±СЂР°Р¶РµРЅРёРµ: {filename}") from exc

        normalized = ImageOps.exif_transpose(source).convert("RGB")
        selected_mode = mode.lower().strip()
        if selected_mode not in {"auto", "baseline", "neural"}:
            raise ValueError("РќРµРґРѕРїСѓСЃС‚РёРјС‹Р№ СЂРµР¶РёРј СѓР»СѓС‡С€РµРЅРёСЏ. РСЃРїРѕР»СЊР·СѓР№С‚Рµ auto, baseline РёР»Рё neural.")

        output_format = self._resolve_format(content_type=content_type, filename=filename)

        if selected_mode == "baseline":
            enhanced = self._enhance_with_baseline(normalized)
            return self._encode_image(enhanced, output_format), self._content_type_for(output_format), BASELINE_MODEL_NAME

        if selected_mode == "neural":
            if self.model is None:
                raise ValueError("РќРµР№СЂРѕСЃРµС‚РµРІР°СЏ РјРѕРґРµР»СЊ РЅРµРґРѕСЃС‚СѓРїРЅР°: РІРµСЃР° РЅРµ РѕР±СѓС‡РµРЅС‹ РёР»Рё torch РЅРµ СѓСЃС‚Р°РЅРѕРІР»РµРЅ.")
            enhanced = self._enhance_with_neural_model(normalized)
            return self._encode_image(enhanced, output_format), self._content_type_for(output_format), DEFAULT_MODEL_NAME

        if self.model is not None:
            enhanced = self._enhance_with_neural_model(normalized)
            return self._encode_image(enhanced, output_format), self._content_type_for(output_format), DEFAULT_MODEL_NAME

        enhanced = self._enhance_with_baseline(normalized)
        return self._encode_image(enhanced, output_format), self._content_type_for(output_format), BASELINE_MODEL_NAME

    def _enhance_with_baseline(self, image: Image.Image) -> Image.Image:
        normalized = ImageOps.autocontrast(image)
        normalized = normalized.filter(ImageFilter.SHARPEN)
        normalized = ImageEnhance.Contrast(normalized).enhance(1.08)
        normalized = ImageEnhance.Sharpness(normalized).enhance(1.15)
        return normalized

    def _enhance_with_neural_model(self, image: Image.Image) -> Image.Image:
        if self.model is None or not torch_available():
            return self._enhance_with_baseline(image)

        original_width, original_height = image.size
        image_array = np.asarray(image, dtype=np.float32) / 255.0
        image_tensor = torch.from_numpy(image_array).permute(2, 0, 1).unsqueeze(0)

        padded_tensor, padding = self._pad_to_multiple(image_tensor, multiple=8)
        padded_tensor = padded_tensor.to(self.device)

        self.model.eval()
        with torch.no_grad():
            restored = self.model(padded_tensor)

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

    def _load_model_if_available(self):
        if not torch_available():
            return None
        if not WEIGHTS_PATH.exists():
            return None

        model = HistologyDenoisingUNet().to(self.device)
        state_dict = torch.load(WEIGHTS_PATH, map_location=self.device)
        model.load_state_dict(state_dict)
        model.eval()
        return model

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
            "message": "РћР±СѓС‡РµРЅРёРµ РЅРµ Р·Р°РїСѓС‰РµРЅРѕ",
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

    def _is_process_alive(self, pid: int) -> bool:
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False

    def _normalize_training_status(self, status: dict[str, object]) -> dict[str, object]:
        if status.get("status") != "running":
            return status
        pid = status.get("pid")
        if pid is not None and self._is_process_alive(int(pid)):
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

    def _parse_datetime(self, value: object) -> datetime | None:
        if not value:
            return None
        try:
            return datetime.strptime(str(value), "%d.%m.%Y %H:%M")
        except ValueError:
            return None

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

