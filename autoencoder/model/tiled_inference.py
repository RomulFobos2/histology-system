"""
Tile-based инференс для Real-ESRGAN.

Большие микроскопические изображения не помещаются целиком в память
на CPU (особенно при ×4 апскейле). Стандартный приём Real-ESRGAN —
разбить вход на квадратные тайлы с небольшим перекрытием (`tile_pad`),
прогнать каждый тайл отдельно через генератор и склеить выходные
тайлы в финальное изображение.

Алгоритм совпадает с реализацией в xinntao/Real-ESRGAN
(RealESRGANer.tile_process), переписан как чистая функция без
зависимости от их обёртки.
"""
from __future__ import annotations

import math

import torch


@torch.no_grad()
def tile_process(
    model: torch.nn.Module,
    image_tensor: torch.Tensor,
    scale: int = 4,
    tile_size: int = 400,
    tile_pad: int = 10,
) -> torch.Tensor:
    """Прогоняет `image_tensor` через `model` тайлами и склеивает результат.

    Args:
        model: обученная сеть (RRDBNet), уже в eval-режиме и на нужном устройстве.
        image_tensor: входной тензор формы (B, C, H, W) на том же устройстве, что и модель.
        scale: коэффициент апскейла модели (для x4plus = 4).
        tile_size: размер квадратного тайла во входных пикселях.
            При tile_size <= 0 тайлинг отключается — прогон цельный.
        tile_pad: ширина перекрытия между тайлами во входных пикселях.

    Returns:
        Тензор формы (B, C, H*scale, W*scale).
    """
    if tile_size is None or tile_size <= 0:
        return model(image_tensor)

    batch, channels, height, width = image_tensor.shape
    output_height = height * scale
    output_width = width * scale
    output_shape = (batch, channels, output_height, output_width)
    output = image_tensor.new_zeros(output_shape)

    tiles_x = math.ceil(width / tile_size)
    tiles_y = math.ceil(height / tile_size)

    for y in range(tiles_y):
        for x in range(tiles_x):
            # Координаты центрального тайла без padding
            ofs_x = x * tile_size
            ofs_y = y * tile_size
            input_start_x = ofs_x
            input_end_x = min(ofs_x + tile_size, width)
            input_start_y = ofs_y
            input_end_y = min(ofs_y + tile_size, height)

            # Координаты с padding (для устранения швов на стыках)
            input_start_x_pad = max(input_start_x - tile_pad, 0)
            input_end_x_pad = min(input_end_x + tile_pad, width)
            input_start_y_pad = max(input_start_y - tile_pad, 0)
            input_end_y_pad = min(input_end_y + tile_pad, height)

            # Размер действительного тайла (без padding)
            input_tile_width = input_end_x - input_start_x
            input_tile_height = input_end_y - input_start_y

            input_tile = image_tensor[
                :,
                :,
                input_start_y_pad:input_end_y_pad,
                input_start_x_pad:input_end_x_pad,
            ]

            # Прогон одного тайла через сеть
            output_tile = model(input_tile)

            # Координаты выхода без padding
            output_start_x = input_start_x * scale
            output_end_x = input_end_x * scale
            output_start_y = input_start_y * scale
            output_end_y = input_end_y * scale

            # Координаты внутри тайла, откуда вырезать «полезную» часть
            output_start_x_tile = (input_start_x - input_start_x_pad) * scale
            output_end_x_tile = output_start_x_tile + input_tile_width * scale
            output_start_y_tile = (input_start_y - input_start_y_pad) * scale
            output_end_y_tile = output_start_y_tile + input_tile_height * scale

            output[
                :,
                :,
                output_start_y:output_end_y,
                output_start_x:output_end_x,
            ] = output_tile[
                :,
                :,
                output_start_y_tile:output_end_y_tile,
                output_start_x_tile:output_end_x_tile,
            ]

    return output
