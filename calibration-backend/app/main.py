import io
import os
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.concurrency import run_in_threadpool
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel

from .constants import DEFAULT_GAME, GAMES
from .detection import YOLOv8Detector

WEIGHTS_DIR = os.environ.get("WEIGHTS_DIR", "weights")
MAX_UPLOAD_BYTES = 15 * 1024 * 1024

Game = Literal[tuple(GAMES)]

detectors: dict[str, YOLOv8Detector] = {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Load once per container instance, not per request.
    for game, cfg in GAMES.items():
        detectors[game] = YOLOv8Detector(os.path.join(WEIGHTS_DIR, cfg["weights"]),
                                         cfg["class_names"])
    yield


app = FastAPI(title="PWDe HUD calibration backend", lifespan=lifespan)


class DetectionOut(BaseModel):
    class_id: int
    class_name: str
    confidence: float
    box: tuple[float, float, float, float]


class DetectResponse(BaseModel):
    width: int
    height: int
    detections: list[DetectionOut]


@app.get("/healthz")
def healthz():
    return {"status": "ok", "models_loaded": sorted(detectors)}


@app.post("/detect", response_model=DetectResponse)
async def detect(
    file: UploadFile = File(..., description="Gameplay screenshot (PNG/JPEG)"),
    conf: float = Query(0.25, ge=0.0, le=1.0, description="Minimum confidence"),
    game: Game = Query(DEFAULT_GAME, description="Which game's HUD model to run"),
):
    data = await file.read()
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "Image too large")
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except (UnidentifiedImageError, OSError):
        raise HTTPException(400, "Could not decode image")

    detections = await run_in_threadpool(detectors[game].detect, img, conf)
    return DetectResponse(width=img.width, height=img.height,
                          detections=[DetectionOut(**d.__dict__) for d in detections])
