FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PORT=8080 \
    WEIGHTS_PATH=/app/weights/model.onnx

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app/ app/
# Produced by training/export_model.py — the build fails here if it's missing,
# which is what we want rather than deploying a service with no model.
COPY weights/model.onnx weights/model.onnx

RUN useradd --create-home --uid 10001 appuser
USER appuser

EXPOSE 8080
# Cloud Run injects $PORT; shell form so it expands.
CMD exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT} --workers 1
