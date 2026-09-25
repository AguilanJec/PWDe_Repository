"""
Exports trained YOLOv8 weights to ONNX and drops them into the backend's
weights/ folder, ready for the Docker build.

The deployed service runs the model with onnxruntime instead of
torch/ultralytics, which keeps the Cloud Run image a few hundred MB instead
of several GB and makes cold starts much faster.

The output file is weights/<game>.onnx, matching the backend's
app/constants.py GAMES entry for that game.

Usage:
    python export_model.py --game mlbb --weights ../runs/detect/mlbb_v18/weights/best.pt
    python export_model.py --game clash_royale --weights ../runs/detect/clash_royale_v2/weights/best.pt
"""

import argparse
import shutil
from pathlib import Path

from ultralytics import YOLO

BACKEND_WEIGHTS = Path(__file__).resolve().parent.parent / "calibration-backend" / "weights"


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", required=True, help="Path to trained best.pt")
    parser.add_argument("--imgsz", type=int, default=960,
                        help="Must match the imgsz the model was trained at.")
    parser.add_argument("--game", required=True, choices=["mlbb", "clash_royale"],
                        help="Which backend model this is; sets the output filename.")
    parser.add_argument("--out", default=None,
                        help="Default: calibration-backend/weights/<game>.onnx")
    args = parser.parse_args()
    args.out = args.out or str(BACKEND_WEIGHTS / f"{args.game}.onnx")

    model = YOLO(args.weights)
    # Fixed input shape (dynamic=False) so onnxruntime can pre-plan memory;
    # opset 12 is supported by every onnxruntime release we'd deploy.
    exported = Path(model.export(format="onnx", imgsz=args.imgsz, opset=12,
                                 dynamic=False, simplify=False))

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(exported, out)
    print(f"\nExported {args.weights} -> {out}")
    print(f"Classes baked into the model: {model.names}")


if __name__ == "__main__":
    main()
