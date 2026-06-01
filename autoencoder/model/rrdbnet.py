"""
RRDBNet — архитектура генератора Real-ESRGAN.

Vendored из официального репозитория https://github.com/xinntao/Real-ESRGAN
(BSD 3-Clause License, © Xintao Wang). Скопирована только архитектура без
зависимости от пакета `basicsr`, потому что последний несовместим с torch 2.x
(использует устаревший импорт torchvision.transforms.functional_tensor).

Это «generator» часть Real-ESRGAN — обученная сеть, которая умеет
4× апскейлить изображение, восстанавливая мелкие детали. В нашем сервисе
используется только в режиме инференса (eval), обучение здесь не делается.

Параметры по умолчанию соответствуют весам RealESRGAN_x4plus.pth:
    num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=4.
"""
from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F


def _pixel_unshuffle(x: torch.Tensor, scale: int) -> torch.Tensor:
    """Обратная операция к pixel-shuffle: ужимает пространственное разрешение
    в `scale` раз, перемещая пиксели в каналы. Используется генератором
    при scale=1/2/3 для работы на пониженном разрешении."""
    batch, channels, height, width = x.size()
    out_channels = channels * (scale ** 2)
    new_height = height // scale
    new_width = width // scale
    x_view = x.view(batch, channels, new_height, scale, new_width, scale)
    return x_view.permute(0, 1, 3, 5, 2, 4).reshape(batch, out_channels, new_height, new_width)


class ResidualDenseBlock(nn.Module):
    """Dense-блок с пятью свёртками 3×3, каждая следующая принимает на вход
    конкатенацию всех предыдущих признаков. Выход складывается с входом
    с коэффициентом 0.2 (стабилизирует обучение глубоких сетей).
    """

    def __init__(self, num_feat: int = 64, num_grow_ch: int = 32) -> None:
        super().__init__()
        self.conv1 = nn.Conv2d(num_feat, num_grow_ch, kernel_size=3, padding=1)
        self.conv2 = nn.Conv2d(num_feat + num_grow_ch, num_grow_ch, kernel_size=3, padding=1)
        self.conv3 = nn.Conv2d(num_feat + 2 * num_grow_ch, num_grow_ch, kernel_size=3, padding=1)
        self.conv4 = nn.Conv2d(num_feat + 3 * num_grow_ch, num_grow_ch, kernel_size=3, padding=1)
        self.conv5 = nn.Conv2d(num_feat + 4 * num_grow_ch, num_feat, kernel_size=3, padding=1)
        self.lrelu = nn.LeakyReLU(negative_slope=0.2, inplace=True)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x1 = self.lrelu(self.conv1(x))
        x2 = self.lrelu(self.conv2(torch.cat((x, x1), dim=1)))
        x3 = self.lrelu(self.conv3(torch.cat((x, x1, x2), dim=1)))
        x4 = self.lrelu(self.conv4(torch.cat((x, x1, x2, x3), dim=1)))
        x5 = self.conv5(torch.cat((x, x1, x2, x3, x4), dim=1))
        return x5 * 0.2 + x


class RRDB(nn.Module):
    """Residual-in-Residual Dense Block — три ResidualDenseBlock,
    выход умножается на 0.2 и складывается со входом. Основной строительный
    блок генератора Real-ESRGAN.
    """

    def __init__(self, num_feat: int, num_grow_ch: int = 32) -> None:
        super().__init__()
        self.rdb1 = ResidualDenseBlock(num_feat, num_grow_ch)
        self.rdb2 = ResidualDenseBlock(num_feat, num_grow_ch)
        self.rdb3 = ResidualDenseBlock(num_feat, num_grow_ch)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = self.rdb1(x)
        out = self.rdb2(out)
        out = self.rdb3(out)
        return out * 0.2 + x


def _make_layer(block_cls, num_blocks: int, **kwargs) -> nn.Sequential:
    return nn.Sequential(*[block_cls(**kwargs) for _ in range(num_blocks)])


class RRDBNet(nn.Module):
    """Real-ESRGAN generator.

    Структура:
      1) `conv_first` — первичная свёртка 3 → 64 канала;
      2) `body` — 23 последовательных RRDB-блока (главный «магический» компонент);
      3) `conv_body` — финальная свёртка тела + global skip;
      4) `upsample` — двойной x2 апскейл через interpolate + Conv (итого ×4);
      5) `conv_hr` + `conv_last` — финальная сборка RGB.

    Для весов x4plus передаётся `scale=4`. Поддерживается также scale=1, 2, 3
    через pixel-unshuffle на входе (стандартный приём Real-ESRGAN).
    """

    def __init__(
        self,
        num_in_ch: int = 3,
        num_out_ch: int = 3,
        scale: int = 4,
        num_feat: int = 64,
        num_block: int = 23,
        num_grow_ch: int = 32,
    ) -> None:
        super().__init__()
        self.scale = scale
        if scale == 2:
            num_in_ch = num_in_ch * 4
        elif scale == 1:
            num_in_ch = num_in_ch * 16

        self.conv_first = nn.Conv2d(num_in_ch, num_feat, kernel_size=3, padding=1)
        self.body = _make_layer(RRDB, num_block, num_feat=num_feat, num_grow_ch=num_grow_ch)
        self.conv_body = nn.Conv2d(num_feat, num_feat, kernel_size=3, padding=1)
        # Два каскада x2 апскейла дают итоговый x4
        self.conv_up1 = nn.Conv2d(num_feat, num_feat, kernel_size=3, padding=1)
        self.conv_up2 = nn.Conv2d(num_feat, num_feat, kernel_size=3, padding=1)
        self.conv_hr = nn.Conv2d(num_feat, num_feat, kernel_size=3, padding=1)
        self.conv_last = nn.Conv2d(num_feat, num_out_ch, kernel_size=3, padding=1)
        self.lrelu = nn.LeakyReLU(negative_slope=0.2, inplace=True)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        if self.scale == 2:
            feat = _pixel_unshuffle(x, scale=2)
        elif self.scale == 1:
            feat = _pixel_unshuffle(x, scale=4)
        else:
            feat = x

        feat = self.conv_first(feat)
        body_feat = self.conv_body(self.body(feat))
        feat = feat + body_feat

        # x2 апскейл #1
        feat = self.lrelu(self.conv_up1(F.interpolate(feat, scale_factor=2, mode="nearest")))
        # x2 апскейл #2 → итого x4
        feat = self.lrelu(self.conv_up2(F.interpolate(feat, scale_factor=2, mode="nearest")))
        out = self.conv_last(self.lrelu(self.conv_hr(feat)))
        return out
