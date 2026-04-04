from fastapi import FastAPI, File, HTTPException, UploadFile
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


@app.post("/train")
def train(epochs: int = 10, batch_size: int = 8, learning_rate: float = 0.001) -> JSONResponse:
    return JSONResponse(
        content=autoencoder_service.train_stub(
            epochs=epochs,
            batch_size=batch_size,
            learning_rate=learning_rate,
        )
    )


@app.post("/enhance")
async def enhance(file: UploadFile = File(...)) -> Response:
    if not file.filename:
        raise HTTPException(status_code=400, detail="Имя файла отсутствует")

    payload = await file.read()
    if not payload:
        raise HTTPException(status_code=400, detail="Файл пуст")

    try:
        enhanced_bytes, content_type = autoencoder_service.enhance_image(
            filename=file.filename,
            payload=payload,
            content_type=file.content_type,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Ошибка обработки изображения") from exc

    headers = {
        "X-Model-Name": autoencoder_service.default_model_name,
        "X-Original-Filename": file.filename,
    }
    return Response(content=enhanced_bytes, media_type=content_type, headers=headers)
