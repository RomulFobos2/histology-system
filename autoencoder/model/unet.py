"""
Собственная denoising U-Net и self-supervised dataset для гистологии.

Эта модель обучается локально на качественных гистологических снимках
с искусственной деградацией. Архитектура остаётся той же, что и в первой
версии сервиса (см. документацию в Свёрточный_автоэнкодер_описание.docx).

После появления Real-ESRGAN U-Net остаётся вторым вариантом —
для демонстрации собственно обученной модели рядом с промышленной SOTA.
"""
from __future__ import annotations

import io
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps

try:
    import torch
    import torch.nn as nn
    import torch.nn.functional as functional
    from torch.utils.data import Dataset
except ImportError:  # pragma: no cover
    torch = None
    nn = None
    functional = None
    Dataset = object


DEFAULT_IMAGE_SIZE = 256


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
