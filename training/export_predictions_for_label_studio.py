"""
Runs a trained YOLOv8 checkpoint over a folder of NOT-YET-labeled frames
and produces a Label Studio import JSON with predictions attached, so
labeling the next batch means correcting boxes instead of drawing every
one from scratch.

Scope: this is for genuinely NEW frames that haven't been imported into
the Label Studio project yet -- it creates new tasks. Attaching
predictions to your existing 32 already-annotated tasks is a separate,
more fragile problem (needs the Label Studio REST API + matching task
IDs by filename) and isn't worth the complexity at this dataset size.

IMPORTANT -- format mismatch between YOLO and Label Studio:
    YOLO:          x_center, y_center, width, height -- normalized 0-1
    Label Studio:  x, y (TOP-LEFT corner), width, height -- PERCENTAGES 0-100
Two things differ at once (the corner used AND the 0-1 vs 0-100 scale),
not just one. Getting this wrong produces boxes that render in roughly
the right area but offset by half a box width/height -- looks almost
right, which makes it an easy bug to miss during a quick glance.

Usage:
    python export_predictions_for_label_studio.py \
        --weights runs/detect/mlbb_v1/weights/best.pt \
        --images extracted_frames/match2_1080x2400 \
        --image-path-template "/data/local-files/?d=mlbb_frames/match2_1080x2400/{filename}" \
        --output predictions_import.json

--image-path-template MUST match how your existing, already-working
tasks reference images -- don't guess this. Check one of your 32
labeled tasks (Data Manager -> click it -> Info panel, or GET
/api/tasks/<id> via the API) and copy its "image" value's pattern
exactly, swapping the filename portion for {filename}. A wrong pattern
here is the most common reason images fail to load after import.
"""

import argparse
import json
from pathlib import Path

from PIL import Image
from ultralytics import YOLO

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def yolo_box_to_label_studio(x_center: float, y_center: float, width: float, height: float) -> dict:
    """YOLO's normalized center-based box -> Label Studio's percentage,
    top-left-based box. Both the corner convention AND the 0-1/0-100
    scale need converting -- this is the one function in this script
    worth double-checking against a known example before trusting it."""
    x_topleft = (x_center - width / 2) * 100
    y_topleft = (y_center - height / 2) * 100
    return {
        "x": max(0.0, x_topleft),
        "y": max(0.0, y_topleft),
        "width": width * 100,
        "height": height * 100,
    }


def build_prediction(image_path: Path, boxes: list, model_version: str) -> dict:
    with Image.open(image_path) as img:
        img_w, img_h = img.size

    results = []
    for box in boxes:
        ls_box = yolo_box_to_label_studio(
            box["x_center"], box["y_center"], box["width"], box["height"]
        )
        results.append({
            "from_name": "label",
            "to_name": "image",
            "type": "rectanglelabels",
            "original_width": img_w,
            "original_height": img_h,
            "value": {**ls_box, "rectanglelabels": [box["class_name"]]},
            "score": box["confidence"],
        })

    avg_score = sum(b["confidence"] for b in boxes) / len(boxes) if boxes else 0.0
    return {"model_version": model_version, "score": avg_score, "result": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", required=True, type=Path)
    parser.add_argument("--images", required=True, type=Path)
    parser.add_argument("--image-path-template", required=True,
                         help="Format string with {filename} placeholder, e.g. "
                              "'/data/local-files/?d=mlbb_frames/match2/{filename}'")
    parser.add_argument("--conf", type=float, default=0.25,
                         help="Confidence threshold. Kept lower than the backend's "
                              "production threshold (0.4) on purpose: over-suggesting "
                              "and letting the labeler delete a wrong box is cheaper "
                              "than under-suggesting and making them draw one from "
                              "scratch.")
    parser.add_argument("--output", type=Path, default=Path("predictions_import.json"))
    parser.add_argument("--model-version", default="mlbb_v1_early")
    args = parser.parse_args()

    model = YOLO(str(args.weights))
    class_names = model.names  # {id: name}, from the checkpoint itself

    image_paths = sorted(p for p in args.images.iterdir() if p.suffix.lower() in IMAGE_EXTENSIONS)
    if not image_paths:
        print(f"No images found in {args.images}")
        return

    tasks = []
    total_boxes = 0

    for image_path in image_paths:
        results = model.predict(str(image_path), conf=args.conf, verbose=False)[0]

        boxes = []
        for box in results.boxes:
            x, y, w, h = box.xywhn[0].tolist()
            boxes.append({
                "class_name": class_names[int(box.cls[0])],
                "confidence": float(box.conf[0]),
                "x_center": x, "y_center": y, "width": w, "height": h,
            })
        total_boxes += len(boxes)

        tasks.append({
            "data": {"image": args.image_path_template.format(filename=image_path.name)},
            "predictions": [build_prediction(image_path, boxes, args.model_version)],
        })

    args.output.write_text(json.dumps(tasks, indent=2))

    avg = total_boxes / len(image_paths) if image_paths else 0
    print(f"Processed {len(image_paths)} images, {total_boxes} total boxes ({avg:.1f} avg/image).")
    print(f"Wrote {args.output} -- import via Label Studio's Data Manager -> Import -> Upload Files.")
    print("\nBefore importing: open the JSON and confirm the first task's 'image' "
          "path matches your existing working tasks' pattern exactly.")


if __name__ == "__main__":
    main()