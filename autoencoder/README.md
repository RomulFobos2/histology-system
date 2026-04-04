# Autoencoder Service

Python-сервис для этапа 6 проекта `histology-system`.

## Что реализовано сейчас

- `GET /health` — проверка доступности сервиса
- `GET /models` — список доступных моделей
- `POST /enhance` — улучшение изображения
- `POST /train` — обучение denoising U-Net на паре `искусственно ухудшенное -> исходное`

Сервис поддерживает 2 режима:

- `baseline` — безопасное улучшение на Pillow
- `neural` — улучшение через обученный `torch` denoising U-Net

По умолчанию используется режим `auto`: если веса модели уже существуют, сервис применяет нейросеть; если весов нет, автоматически использует baseline.

## Как устроено обучение

В папку `autoencoder/data/` кладутся хорошие гистологические изображения.

Во время обучения сервис сам делает искусственную деградацию:

- blur;
- downscale/upscale;
- шум;
- падение контраста и яркости;
- JPEG-артефакты.

После этого сеть учится восстанавливать исходное качественное изображение.

## Установка

```powershell
cd autoencoder
python -m venv venv
venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Если установка `torch` через `requirements.txt` не сработала на конкретной машине, установи его отдельно по официальной инструкции PyTorch, а затем повторно выполни `pip install -r requirements.txt`.

## Запуск

```powershell
cd autoencoder
venv\Scripts\Activate.ps1
uvicorn app:app --host 127.0.0.1 --port 8000 --reload
```

## Подготовка данных для обучения

Положи обучающие изображения в папку:

```text
autoencoder/data/
```

Поддерживаемые форматы:

- `.jpg`
- `.jpeg`
- `.png`
- `.tif`
- `.tiff`

Можно использовать вложенные папки: сервис соберёт изображения рекурсивно.

## Обучение

Через Python:

```powershell
python train.py
```

Или через API:

```powershell
curl -X POST "http://127.0.0.1:8000/train?epochs=15&batch_size=4&learning_rate=0.0005&image_size=256"
```

После обучения веса будут сохранены в:

```text
autoencoder/weights/latest_autoencoder.pt
autoencoder/weights/latest_metadata.json
```

## Улучшение изображения

Endpoint `/enhance` принимает файл и опциональный режим:

- `auto`
- `baseline`
- `neural`

Пример формы:

```text
file=<изображение>, mode=auto
```

## Практические рекомендации

- Для первого адекватного результата лучше использовать не менее 50-100 качественных гистологических изображений.
- Если нейросеть всё ещё "мылит" детали, сначала увеличивай число эпох, а не усложняй API.
- Для CPU-обучения разумный старт: `epochs=15-30`, `batch_size=2-4`, `image_size=256`.

## Проверка

```powershell
Invoke-WebRequest http://127.0.0.1:8000/health
```

Ожидаемый ответ:

```json
{"status":"ok"}
```
