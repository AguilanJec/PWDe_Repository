"""
Takes a flat folder of labeled images + YOLO-format .txt labels
(as exported directly from Label Studio or CVAT) and:

  1. Splits them into train/val/test (70/20/10 by default), keeping
     each image with its matching label file.
  2. Prints a per-class detection count, so you can see at a glance
     whether skill_upgrade (the small-object risk class) or the
     4-skill layout is under-represented before you spend a training
     run finding out the hard way.

Usage:
    python prepare_dataset.py \
        --source /path/to/label_studio_export \
        --dest mlbb_dataset \
        --train 0.7 --val 0.2 --test 0.1

Expects `source` to contain images (.jpg/.png) and same-named .txt
label files side by side — the default Label Studio/CVAT YOLO export
layout. If your export already separates images/ and labels/, point
--source at the parent folder that contains both.
"""

import argparse
import random
import shutil
from collections import Counter
from pathlib import Path

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def find_pairs(source: Path):
    """Return list of (image_path, label_path) for every image that has
    a matching label file. Images without labels are skipped with a
    warning — silently training on unlabeled images would just teach
    the model that those buttons don't exist."""
    pairs = []
    skipped = []

    # Handle both flat exports and images/ + labels/ subfolder exports.
    image_dir = source / "images" if (source / "images").exists() else source
    label_dir = source / "labels" if (source / "labels").exists() else source

    for img_path in image_dir.iterdir():
        if img_path.suffix.lower() not in IMAGE_EXTENSIONS:
            continue
        label_path = label_dir / f"{img_path.stem}.txt"
        if label_path.exists():
            pairs.append((img_path, label_path))
        else:
            skipped.append(img_path.name)

    if skipped:
        print(f"WARNING: {len(skipped)} images have no matching label file, skipping:")
        for name in skipped[:10]:
            print(f"    {name}")
        if len(skipped) > 10:
            print(f"    ... and {len(skipped) - 10} more")

    return pairs


def report_class_distribution(pairs, class_names):
    """Count detections per class across the whole labeled set. This is
    the cheap sanity check for the 3-skill/4-skill imbalance risk we
    flagged earlier — if skill_button count looks lopsided relative to
    how many 4-skill heroes you actually recorded, fix it before training,
    not after."""
    counts = Counter()
    for _, label_path in pairs:
        for line in label_path.read_text().splitlines():
            line = line.strip()
            if not line:
                continue
            class_id = int(line.split()[0])
            counts[class_id] += 1

    print("\nPer-class detection counts (across all labeled images):")
    for class_id, name in enumerate(class_names):
        print(f"  {name:>15}: {counts.get(class_id, 0)}")
    print()


def split_and_copy(pairs, dest: Path, train_frac, val_frac, test_frac, seed):
    assert abs(train_frac + val_frac + test_frac - 1.0) < 1e-6, \
        "train/val/test fractions must sum to 1.0"

    rng = random.Random(seed)
    shuffled = pairs[:]
    rng.shuffle(shuffled)

    n = len(shuffled)
    n_train = int(n * train_frac)
    n_val = int(n * val_frac)

    splits = {
        "train": shuffled[:n_train],
        "val": shuffled[n_train:n_train + n_val],
        "test": shuffled[n_train + n_val:],
    }

    for split_name, split_pairs in splits.items():
        img_out = dest / "images" / split_name
        lbl_out = dest / "labels" / split_name
        img_out.mkdir(parents=True, exist_ok=True)
        lbl_out.mkdir(parents=True, exist_ok=True)

        for img_path, label_path in split_pairs:
            shutil.copy2(img_path, img_out / img_path.name)
            shutil.copy2(label_path, lbl_out / label_path.name)

        print(f"{split_name}: {len(split_pairs)} images")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--dest", required=True, type=Path)
    parser.add_argument("--train", type=float, default=0.7)
    parser.add_argument("--val", type=float, default=0.2)
    parser.add_argument("--test", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    class_names = [
        "recall", "regen", "spell", "buy_item", "use_item",
        "basic_attack", "skill_button", "skill_upgrade", "joystick",
    ]

    pairs = find_pairs(args.source)
    print(f"Found {len(pairs)} labeled image/label pairs.\n")

    if not pairs:
        print("No labeled pairs found — check --source path and file naming.")
        return

    report_class_distribution(pairs, class_names)
    split_and_copy(pairs, args.dest, args.train, args.val, args.test, args.seed)


if __name__ == "__main__":
    main()