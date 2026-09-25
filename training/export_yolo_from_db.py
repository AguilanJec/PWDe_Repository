"""
Exports the FULL Label Studio project straight from its sqlite3 database
into YOLO format (images/ + labels/ + classes.txt), bypassing Label
Studio's own export feature.

Why this exists: Label Studio's Data Manager "Export -> YOLO" was only
producing the original 58 manually-labeled tasks, even though the
project's sqlite3 db (label-studio-data/label_studio.sqlite3) shows all
204 tasks have a real, non-empty annotation. This reads the annotations
directly from task_completion instead of trusting that export path.

Two things this script has to handle that a naive export wouldn't:

  1. Duplicate annotations per task. 9 tasks in this project have two
     rows in task_completion for the same task_id -- an original with
     real boxes and a second, empty one created later in bulk (all 9
     share the exact same updated_at). Blindly using "the last
     annotation" per task would silently produce an empty label file
     for those 9 images. Instead we pick the annotation with the most
     actual rectanglelabels boxes per task, not the most recent
     (task_completion.result_count is not usable for this -- it's 0 on
     every row in this db, even ones with real content).

  2. Mixed annotation types. Each annotation's `result` list contains
     the rectanglelabels boxes we want, PLUS this project's layout_mode
     (choices) and hero (textarea) fields, which are not spatial boxes
     and must not become label lines.

Label Studio's percentage/top-left box format is converted to YOLO's
normalized/center format here directly (see label_studio_box_to_yolo).

Usage:
    python export_yolo_from_db.py \\
        --db ../label-studio-data/label_studio.sqlite3 \\
        --frames-root extracted_frames \\
        --dest label_studio_full_export
"""

import argparse
import json
import re
import shutil
import sqlite3
from collections import defaultdict
from pathlib import Path

# Must match app/constants.py CLASS_NAMES exactly (see remap_label_studio_export.py).
CANONICAL_CLASS_NAMES = [
    "recall",
    "regen",
    "spell",
    "buy_item",
    "use_item",
    "basic_attack",
    "skill_button",
    "skill_upgrade",
    "joystick",
]

IMAGE_PATH_RE = re.compile(r"d=mlbb_frames/([^/]+)/([^/&]+)")


def label_studio_box_to_yolo(value: dict) -> tuple[float, float, float, float]:
    """LS: x, y = TOP-LEFT corner, percentages 0-100.
    YOLO: x_center, y_center, width, height, normalized 0-1.
    Both the corner convention AND the 0-100/0-1 scale differ."""
    x_center = (value["x"] + value["width"] / 2) / 100
    y_center = (value["y"] + value["height"] / 2) / 100
    width = value["width"] / 100
    height = value["height"] / 100
    return x_center, y_center, width, height


def count_boxes(result_json: str | None) -> int:
    """Number of rectanglelabels entries actually in the result JSON.
    task_completion.result_count is NOT reliable here -- it's 0 on every
    row in this db, including ones with a full result JSON, so it can't
    be used to tell a real annotation from an empty one."""
    if not result_json:
        return 0
    return sum(1 for item in json.loads(result_json) if item.get("type") == "rectanglelabels")


def pick_best_annotation(rows: list[tuple]) -> tuple | None:
    """rows: list of (id, result, was_cancelled, result_count, updated_at)
    for a single task_id. Picks the one with the most actual boxes, not
    the most recent -- see module docstring for why."""
    live = [r for r in rows if not r[2]]  # drop was_cancelled
    if not live:
        return None
    best = max(live, key=lambda r: count_boxes(r[1]))
    return best if count_boxes(best[1]) > 0 else None


def load_annotations(db_path: Path) -> dict[int, tuple]:
    con = sqlite3.connect(str(db_path))
    cur = con.cursor()
    cur.execute(
        "SELECT id, task_id, result, was_cancelled, result_count, updated_at "
        "FROM task_completion"
    )
    by_task = defaultdict(list)
    for row_id, task_id, result, was_cancelled, result_count, updated_at in cur.fetchall():
        by_task[task_id].append((row_id, result, was_cancelled, result_count, updated_at))

    best = {}
    empty_tasks = []
    for task_id, rows in by_task.items():
        chosen = pick_best_annotation(rows)
        if chosen is None:
            empty_tasks.append(task_id)
            continue
        best[task_id] = chosen

    if empty_tasks:
        print(f"WARNING: {len(empty_tasks)} tasks have no usable (non-empty, "
              f"non-cancelled) annotation, skipping: {sorted(empty_tasks)}")

    cur.execute("SELECT id, data FROM task")
    tasks = {task_id: json.loads(data) for task_id, data in cur.fetchall()}
    con.close()
    return best, tasks


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db", required=True, type=Path,
                         help="Path to label_studio.sqlite3")
    parser.add_argument("--frames-root", required=True, type=Path,
                         help="Local folder that Label Studio's "
                              "'mlbb_frames' local-files storage points "
                              "at (contains one subfolder per clip, e.g. "
                              "extracted_frames/)")
    parser.add_argument("--dest", required=True, type=Path)
    args = parser.parse_args()

    annotations, tasks = load_annotations(args.db)
    print(f"{len(tasks)} tasks total, {len(annotations)} with a usable annotation.")

    img_out = args.dest / "images"
    lbl_out = args.dest / "labels"
    img_out.mkdir(parents=True, exist_ok=True)
    lbl_out.mkdir(parents=True, exist_ok=True)

    written = 0
    missing_images = []

    for task_id, (row_id, result_json, _, _, _) in sorted(annotations.items()):
        task_data = tasks[task_id]
        match = IMAGE_PATH_RE.search(task_data["image"])
        if not match:
            print(f"  WARNING: task {task_id} has an unrecognized image path "
                  f"{task_data['image']!r}, skipping")
            continue
        subfolder, filename = match.group(1), match.group(2)

        src_image = args.frames_root / subfolder / filename
        if not src_image.exists():
            missing_images.append(str(src_image))
            continue

        out_name = f"{subfolder}__{filename}"
        shutil.copy2(src_image, img_out / out_name)

        lines = []
        for item in json.loads(result_json):
            if item.get("type") != "rectanglelabels":
                continue
            class_name = item["value"]["rectanglelabels"][0]
            if class_name not in CANONICAL_CLASS_NAMES:
                print(f"  WARNING: task {task_id} has unknown class "
                      f"{class_name!r}, skipping that box")
                continue
            class_id = CANONICAL_CLASS_NAMES.index(class_name)
            cx, cy, w, h = label_studio_box_to_yolo(item["value"])
            lines.append(f"{class_id} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}")

        label_path = lbl_out / f"{Path(out_name).stem}.txt"
        label_path.write_text("\n".join(lines) + ("\n" if lines else ""))
        written += 1

    (args.dest / "classes.txt").write_text("\n".join(CANONICAL_CLASS_NAMES) + "\n")

    print(f"\nWrote {written} image/label pairs to {args.dest}")
    if missing_images:
        print(f"\nWARNING: {len(missing_images)} images referenced by tasks were "
              f"not found under --frames-root, skipped:")
        for p in missing_images[:10]:
            print(f"    {p}")
        if len(missing_images) > 10:
            print(f"    ... and {len(missing_images) - 10} more")


if __name__ == "__main__":
    main()
