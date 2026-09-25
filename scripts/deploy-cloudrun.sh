#!/usr/bin/env bash
# Builds calibration-backend with Cloud Build and deploys it to Cloud Run.
#
# Prereqs:
#   - gcloud auth login && gcloud config set project <PROJECT_ID>
#   - calibration-backend/weights/model.onnx exists
#     (run: cd training && python export_model.py --weights <best.pt>)
#
# Usage:
#   scripts/deploy-cloudrun.sh
#   REGION=europe-west1 SERVICE=mlbb-calibration scripts/deploy-cloudrun.sh
#   PUBLIC=1 scripts/deploy-cloudrun.sh   # allow unauthenticated requests

set -euo pipefail

SERVICE="${SERVICE:-mlbb-calibration}"
REGION="${REGION:-asia-southeast1}"
PROJECT="${PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
BACKEND_DIR="$(cd "$(dirname "$0")/../calibration-backend" && pwd)"

if [[ -z "$PROJECT" ]]; then
  echo "No GCP project set. Run: gcloud config set project <PROJECT_ID>" >&2
  exit 1
fi
if [[ ! -f "$BACKEND_DIR/weights/model.onnx" ]]; then
  echo "Missing $BACKEND_DIR/weights/model.onnx — run training/export_model.py first." >&2
  exit 1
fi

if [[ "${PUBLIC:-0}" == "1" ]]; then
  AUTH_FLAG="--allow-unauthenticated"
else
  AUTH_FLAG="--no-allow-unauthenticated"
fi

gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com --project "$PROJECT"

# 960x960 inference on CPU: 1 vCPU / 1 GiB is plenty for a ~12 MB model.
# Concurrency is kept low because each request is CPU-bound.
gcloud run deploy "$SERVICE" \
  --project "$PROJECT" \
  --region "$REGION" \
  --source "$BACKEND_DIR" \
  --cpu 1 \
  --memory 1Gi \
  --concurrency 4 \
  --min-instances 0 \
  --max-instances 3 \
  --timeout 60 \
  $AUTH_FLAG
