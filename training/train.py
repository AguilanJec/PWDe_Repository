"""
Fine-tunes YOLOv8 on the MLBB button dataset.

Assumes prepare_dataset.py has already populated mlbb_dataset/{images,labels}
and mlbb_data.yaml points at it.

Usage:
    python train.py
    python train.py --model yolov8n.pt --epochs 150 --imgsz 1280

Notes (matching the earlier pipeline discussion):
  - Starts from COCO-pretrained weights and fine-tunes — training from
    scratch is hopeless with a dataset this size (~500 images).
  - imgsz defaults to 960, not YOLO's default 640, because skill_upgrade
    icons are small relative to the frame and YOLO is weaker on small
    objects at lower input resolutions.
  - fliplr is disabled: the MLBB HUD is asymmetric (skills on one side,
    joystick on the other), so a horizontal flip would create a
    physically impossible frame rather than a useful augmentation.
  - mosaic is left on (YOLOv8 default) — one of the best free wins for
    a small dataset, since it synthetically composes more varied training
    images from the limited real pool.
  - patience=20 enables early stopping so a plateaued run doesn't burn
    the rest of your GPU time budget for nothing.
"""

import argparse
from pathlib import Path

import yaml
from ultralytics import YOLO


def resolve_data_yaml(data_arg: str) -> str:
    """Ultralytics resolves a relative `path:` in the dataset YAML against its
    global datasets_dir setting, NOT the YAML's own folder, so
    `path: ./mlbb_dataset` ends up pointing somewhere like
    ~/datasets/mlbb_dataset and training fails with "Dataset images not
    found". Rewrite it to an absolute path next to the YAML and hand
    ultralytics that resolved copy instead."""
    yaml_path = Path(data_arg).resolve()
    cfg = yaml.safe_load(yaml_path.read_text())
    root = Path(cfg.get("path") or ".")
    if not root.is_absolute():
        root = (yaml_path.parent / root).resolve()
    if not (root / cfg["train"]).exists():
        raise FileNotFoundError(f"Training images not found at {root / cfg['train']}")
    cfg["path"] = str(root)
    resolved = yaml_path.with_name(yaml_path.stem + ".resolved.yaml")
    resolved.write_text(yaml.safe_dump(cfg, sort_keys=False))
    return str(resolved)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", default="mlbb_data.yaml")
    parser.add_argument("--model", default="yolov8n.pt",
                         help="Pretrained checkpoint to fine-tune from. "
                              "Use yolov8n or yolov8s for fast iteration "
                              "given the contest timeline — not m/l/x.")
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--imgsz", type=int, default=960)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--patience", type=int, default=20)
    parser.add_argument("--workers", type=int, default=2,
                         help="Dataloader worker processes. Kept low because on "
                              "Windows each worker is a spawned process that "
                              "reloads torch+CUDA DLLs (~1 GB commit each); "
                              "YOLO's default of 8 exhausts the commit limit "
                              "and surfaces as a misleading CUDA out-of-memory.")
    parser.add_argument("--name", default="mlbb_v1",
                         help="Run name — outputs land in runs/detect/<name>/")
    args = parser.parse_args()

    data_yaml = resolve_data_yaml(args.data)
    model = YOLO(args.model)

    model.train(
        data=data_yaml,
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        patience=args.patience,
        workers=args.workers,
        fliplr=0.0,     # asymmetric HUD — do not mirror
        name=args.name,
    )

    # Ultralytics auto-increments the run name (mlbb_v1 -> mlbb_v18) when the
    # folder already exists, so take the real output dir from the trainer
    # rather than rebuilding it from args.name.
    save_dir = Path(model.trainer.save_dir)
    best_pt = save_dir / "weights" / "best.pt"

    print("\nTraining complete. Running validation for per-class metrics...\n")
    # Without project/name, val() creates a sibling run dir (mlbb_v182);
    # keep its outputs inside this run instead.
    metrics = model.val(project=str(save_dir), name="val", exist_ok=True)

    # Per-class precision/recall/mAP — this is what actually tells you
    # whether skill_upgrade is underperforming while the fixed classes
    # look fine, which a single overall mAP number would hide.
    # box.ap50 / box.ap are indexed by position in ap_class_index (classes
    # present in the val set), not by class id. box.maps is mAP50-95.
    print("Per-class results:")
    box = metrics.box
    for idx, cls_id in enumerate(box.ap_class_index):
        class_name = model.names[int(cls_id)]
        print(f"  {class_name:>15}  mAP50: {box.ap50[idx]:.3f}  "
              f"mAP50-95: {box.ap[idx]:.3f}")
    missing = set(model.names) - {int(c) for c in box.ap_class_index}
    for cls_id in sorted(missing):
        print(f"  {model.names[cls_id]:>15}  (no instances in val set)")

    print(f"\nBest weights saved to: {best_pt}")
    print("Point YOLOv8Detector(weights_path=...) at that file in "
          "calibration-backend/app/detection.py to wire it into the API.")


if __name__ == "__main__":
    main()