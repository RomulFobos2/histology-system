from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse, Response

from model.autoencoder import AutoencoderService


app = FastAPI(
    title="Histology Autoencoder Service",
    description="Python API для улучшения микроскопических изображений",
    version="0.1.0",
)

autoencoder_service = AutoencoderService()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/models")
def models() -> JSONResponse:
    return JSONResponse(content=autoencoder_service.list_models())


@app.get("/metrics")
def metrics() -> JSONResponse:
    return JSONResponse(content=autoencoder_service.get_metrics())


@app.get("/training/status")
def training_status() -> JSONResponse:
    return JSONResponse(content=autoencoder_service.get_training_status())


@app.get("/training/history")
def training_history() -> JSONResponse:
    return JSONResponse(content=autoencoder_service.get_training_history())


@app.post("/train")
def train(
    epochs: int = 15,
    batch_size: int = 4,
    learning_rate: float = 0.0005,
    image_size: int = 256,
) -> JSONResponse:
    result = autoencoder_service.start_training_async(
        epochs=epochs,
        batch_size=batch_size,
        learning_rate=learning_rate,
        image_size=image_size,
    )
    status_code = 202 if result.get("status") == "accepted" else 409 if result.get("status") == "busy" else 400
    return JSONResponse(content=result, status_code=status_code)


@app.post("/enhance")
async def enhance(file: UploadFile = File(...), mode: str = Form("auto")) -> Response:
    if not file.filename:
        raise HTTPException(status_code=400, detail="Имя файла отсутствует")

    payload = await file.read()
    if not payload:
        raise HTTPException(status_code=400, detail="Файл пуст")

    try:
        enhanced_bytes, content_type, model_name = autoencoder_service.enhance_image(
            filename=file.filename,
            payload=payload,
            content_type=file.content_type,
            mode=mode,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Ошибка обработки изображения") from exc

    headers = {
        "X-Model-Name": model_name,
        "X-Original-Filename": file.filename,
    }
    return Response(content=enhanced_bytes, media_type=content_type, headers=headers)
