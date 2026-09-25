#!/usr/bin/env bash
# Creates two separate virtual environments, matching the two
# independent dependency sets in this repo — see the reasoning in
# the project discussion: training's torch/ultralytics stack is heavy
# and deployment-irrelevant, the backend's fastapi stack is light and
# deployment-relevant. Keeping them apart keeps the deployed Cloud Run
# image lean and avoids the two dependency sets fighting each other
# during resolution.

set -e

echo "Setting up calibration-backend venv..."
python3 -m venv calibration-backend/.venv
calibration-backend/.venv/bin/pip install -q --upgrade pip
calibration-backend/.venv/bin/pip install -q -r calibration-backend/requirements.txt
echo "  -> activate with: source calibration-backend/.venv/bin/activate"

echo "Setting up training venv..."
python3 -m venv training/.venv
training/.venv/bin/pip install -q --upgrade pip
training/.venv/bin/pip install -q -r training/requirements.txt
echo "  -> activate with: source training/.venv/bin/activate"

echo
echo "Done. Label Studio is intentionally NOT included in either venv —"
echo "it bundles its own heavy, unrelated web-server dependencies."
echo "Run it standalone instead: pipx install label-studio"