# Autoencoder Service

Python-микросервис улучшения микроскопических изображений для этапа 6 проекта `histology-system`.

## Что реализовано сейчас

- `GET /health` — проверка доступности сервиса
- `GET /models` — список доступных моделей (baseline, Real-ESRGAN, наш U-Net)
- `GET /metrics` — метрики активной модели
- `GET /training/status`, `GET /training/history`, `DELETE /training/history`, `POST /training/reset-status`
- `POST /enhance` — улучшение изображения (multipart `file` + поле `mode`)
- `POST /train` — обучение нашей U-Net на парах «искусственно ухудшенное → исходное»

## Гибридная схема: три модели в одном сервисе

| Модель | Тип | Размер выхода | Описание |
|---|---|---|---|
| `baseline-pillow-enhancer` | Pillow-пайплайн | как у входа | autocontrast → SHARPEN → Contrast(1.08) → Sharpness(1.15). Fallback без нейросети. |
| `RealESRGAN_x4plus` | Real-ESRGAN, RRDB-генератор, 23 блока | **4× апскейл** | Промышленная state-of-the-art модель с предобученными весами. Обучена с perceptual (VGG19) + adversarial loss. Тяжёлая визуальная резкость, восстановление мелких деталей. |
| `histology-denoising-unet` | Наш U-Net (3 ступени, 32/64/128/256 каналов) | как у входа | Обучается локально на гистологических снимках с искусственной деградацией (blur, шум, JPEG-артефакты). Loss = L1 + 0.15·edge-L1. |

## Режимы `/enhance`

- `auto` — Real-ESRGAN, если веса доступны; иначе наш U-Net, иначе baseline (рекомендуется).
- `esrgan` — принудительно Real-ESRGAN (4× апскейл).
- `unet` — принудительно наш U-Net (того же разрешения, что и вход).
- `neural` — алиас `esrgan` (обратная совместимость со старым Java-клиентом).
- `baseline` — Pillow-пайплайн.

## Архитектура Real-ESRGAN (RRDBNet)

Вместо тяжёлой связки `realesrgan` + `basicsr` (последний несовместим с torch 2.x), архитектура `RRDBNet` **vendored** прямо в `model/rrdbnet.py` (~200 строк, BSD 3-Clause, источник — `xinntao/Real-ESRGAN`).

Основные блоки:
- `ResidualDenseBlock_5C` — dense-блок с 5 свёртками 3×3 и residual scaling × 0.2;
- `RRDB` (Residual-in-Residual Dense Block) — три ResidualDenseBlock + residual scaling;
- `RRDBNet` — 23 RRDB + двойной x2-апскейл через nearest interpolation + Conv. Итого x4.

Большие изображения прогоняются тайлами 400×400 с `tile_pad=10` (см. `model/tiled_inference.py`).

## Как устроено обучение нашей U-Net

В папку `autoencoder/data/` кладутся качественные гистологические изображения.
Во время обучения сервис сам делает искусственную деградацию: blur, downscale/upscale, гауссов шум, изменения контраста/яркости, JPEG-артефакты. После этого сеть учится восстанавливать исходное изображение.

## Установка

```powershell
cd autoencoder
python -m venv venv
venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Если установка `torch` через `requirements.txt` не сработала на конкретной машине, установи его отдельно по официальной инструкции PyTorch, а затем повторно выполни `pip install -r requirements.txt`.

## Веса предобученной модели

Веса `RealESRGAN_x4plus.pth` (~64 МБ) **хранятся в репозитории** в `autoencoder/weights/pretrained/`. После клона репозитория сервис работает офлайн с первого запуска.

Источник весов: https://github.com/xinntao/Real-ESRGAN/releases (v0.1.0).

## Запуск

```powershell
cd autoencoder
venv\Scripts\Activate.ps1
uvicorn app:app --host 127.0.0.1 --port 8000 --reload
```

## Подготовка данных для обучения U-Net

Положи обучающие изображения в папку `autoencoder/data/`. Поддерживаемые форматы: `.jpg`, `.jpeg`, `.png`, `.tif`, `.tiff`. Допустимы вложенные папки.

## Обучение U-Net

Через Python:

```powershell
python train.py
```

Или через API:

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8000/train?epochs=15&batch_size=4&learning_rate=0.0005&image_size=256"
```

После обучения веса сохраняются в `autoencoder/weights/latest_autoencoder.pt`, метаданные — в `latest_metadata.json`.

## Улучшение изображения

Endpoint `/enhance` принимает multipart-форму `file` + `mode`:

```text
file=<изображение>, mode=auto | esrgan | unet | neural | baseline
```

В ответе:
- тело — байты PNG/JPEG;
- заголовок `X-Model-Name` — имя реально использованной модели;
- заголовок `X-Original-Filename` — исходное имя файла.

## Практические замечания

- Real-ESRGAN на CPU обрабатывает 800×600 за ~45 секунд (tile-based). На GPU — секунды.
- Для адекватного обучения U-Net нужно минимум 50–100 качественных гистологических изображений.
- Для CPU-обучения U-Net разумный старт: `epochs=15–30`, `batch_size=2–4`, `image_size=256`.

## Проверка

```powershell
Invoke-WebRequest http://127.0.0.1:8000/health    # {"status":"ok"}
Invoke-WebRequest http://127.0.0.1:8000/models    # массив из 3 моделей
```
