# Autoencoder Service

Python-сервис для этапа 6 проекта `histology-system`.

## Что реализовано сейчас

- `GET /health` — проверка доступности сервиса
- `GET /models` — список доступных моделей
- `POST /enhance` — улучшение изображения
- `POST /train` — заглушка для следующего шага

Текущая версия использует безопасный baseline-пайплайн на Pillow. Это не обученный свёрточный автоэнкодер, а стартовая реализация API и интеграции. Структура подготовлена для последующей замены обработчика на настоящую модель.

## Установка

```powershell
cd autoencoder
python -m venv venv
venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## Запуск

```powershell
cd autoencoder
venv\Scripts\Activate.ps1
uvicorn app:app --host 127.0.0.1 --port 8000 --reload
```

## Проверка

```powershell
Invoke-WebRequest http://127.0.0.1:8000/health
```

Ожидаемый ответ:

```json
{"status":"ok"}
```
