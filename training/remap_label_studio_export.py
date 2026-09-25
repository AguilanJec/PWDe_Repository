"""
Label Studio's YOLO export writes its own classes.txt alongside the
label .txt files, and the class IDs inside those label files are
indices into THAT classes.txt — not necessarily in the same order as
app/constants.py's CLASS_NAMES, even if the labeling config lists them
in that order. Different Label Studio versions have handled export
ordering differently (config order vs. alphabetical vs. label-creation
order), so this should be verified after every export, not assumed.

If the orders don't match and you skip this step, every label file
trains against the WRONG class silently — no error, no crash, just a
model that's quietly confused about which icon is which. This is
exactly the kind of bug that's expensive to catch after the fact
(a training run "sort of works" with confusing per-class metrics) and
cheap to catch now.

Usage:
    python remap_label_studio_export.py --export /path/to/label_studio_export

    # If the export also contains non-box metadata classes (e.g. this
    # project's layout_mode Choices field leaking into classes.txt as
    # "3-skill"/"4-skill" -- Label Studio's YOLO exporter has been known
    # to sweep Choices/TextArea control values into the class list
    # alongside real RectangleLabels classes), strip them explicitly:
    python remap_label_studio_export.py --export /path/to/export --strip-classes 3-skill 4-skill

This:
  1. Reads the export's own classes.txt
  2. Drops any --strip-classes entries entirely (including any label
     lines that reference them -- these are typically bogus full-image
     placeholder boxes for a non-spatial annotation, not real objects,
     and must not survive into training data)
  3. Compares what's left against the canonical CLASS_NAMES below
  4. If they already match, does nothing (beyond stripping) and says so
  5. If they don't, rewrites every label .txt file's class IDs to match
     the canonical order, in place, and backs up the originals first
"""

import argparse
import shutil
from pathlib import Path

# Must match app/constants.py CLASS_NAMES exactly.
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


def load_export_classes(export_dir: Path) -> list[str]:
    classes_file = export_dir / "classes.txt"
    if not classes_file.exists():
        raise FileNotFoundError(
            f"No classes.txt found in {export_dir} — is this a Label Studio "
            f"YOLO export? Expected it alongside images/ and labels/."
        )
    return [line.strip() for line in classes_file.read_text().splitlines() if line.strip()]


def find_label_files(export_dir: Path) -> list[Path]:
    label_dir = export_dir / "labels" if (export_dir / "labels").exists() else export_dir
    return sorted(label_dir.glob("*.txt"))


def remap_label_files(
    label_files: list[Path],
    old_id_to_new_id: dict[int, int],
    stripped_old_ids: set[int],
    export_dir: Path,
):
    backup_dir = export_dir / "labels_before_remap"
    backup_dir.mkdir(exist_ok=True)

    total_stripped_lines = 0

    for label_path in label_files:
        shutil.copy2(label_path, backup_dir / label_path.name)

        new_lines = []
        for line in label_path.read_text().splitlines():
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            old_id = int(parts[0])

            if old_id in stripped_old_ids:
                total_stripped_lines += 1
                continue  # drop entirely -- not a real object

            if old_id not in old_id_to_new_id:
                print(f"  WARNING: {label_path.name} has unknown class id {old_id}, leaving unchanged")
                new_lines.append(line)
                continue

            parts[0] = str(old_id_to_new_id[old_id])
            new_lines.append(" ".join(parts))

        label_path.write_text("\n".join(new_lines) + ("\n" if new_lines else ""))

    if total_stripped_lines:
        print(f"Stripped {total_stripped_lines} non-box metadata lines "
              f"(e.g. layout_mode placeholder annotations) across all label files.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--export", required=True, type=Path,
                         help="Path to the Label Studio YOLO export folder")
    parser.add_argument("--strip-classes", nargs="*", default=[],
                         help="Class names to remove entirely from classes.txt and "
                              "from every label file (e.g. Choices/TextArea field "
                              "values that ended up in classes.txt by mistake)")
    args = parser.parse_args()

    export_classes = load_export_classes(args.export)
    strip_set = set(args.strip_classes)

    print(f"Export classes.txt order: {export_classes}")
    if strip_set:
        print(f"Stripping (treated as non-box metadata, not real classes): {sorted(strip_set)}")
    print(f"Canonical order (app/constants.py): {CANONICAL_CLASS_NAMES}\n")

    stripped_old_ids = {i for i, name in enumerate(export_classes) if name in strip_set}
    remaining_classes = [name for name in export_classes if name not in strip_set]

    if set(remaining_classes) != set(CANONICAL_CLASS_NAMES):
        missing = set(CANONICAL_CLASS_NAMES) - set(remaining_classes)
        extra = set(remaining_classes) - set(CANONICAL_CLASS_NAMES)
        print("ERROR: class name sets don't match between export and canonical list, "
              "even after stripping.")
        if missing:
            print(f"  Missing from export: {missing}")
        if extra:
            print(f"  Unexpected in export (typo in labeling? check labeling_config.xml, "
                  f"or add to --strip-classes if this is metadata, not a box class): {extra}")
        return

    if remaining_classes == CANONICAL_CLASS_NAMES and not stripped_old_ids:
        print("Export order already matches canonical order — no remapping needed.")
        return

    old_id_to_new_id = {
        old_id: CANONICAL_CLASS_NAMES.index(name)
        for old_id, name in enumerate(export_classes)
        if name not in strip_set
    }
    print("ID remap (old -> new):")
    for old_id, name in enumerate(export_classes):
        if name in strip_set:
            print(f"  {old_id} ({name}) -> DROPPED")
        else:
            print(f"  {old_id} ({name}) -> {old_id_to_new_id[old_id]}")

    label_files = find_label_files(args.export)
    print(f"\nProcessing {len(label_files)} label files...")
    remap_label_files(label_files, old_id_to_new_id, stripped_old_ids, args.export)

    # Critical for idempotency: rewrite classes.txt to the canonical list too.
    # Without this, a second run would re-read the STALE original classes.txt,
    # recompute the same old->new mapping, and apply it AGAIN to label files
    # that already use canonical ids -- silently corrupting already-fixed
    # data (e.g. treating a correct "recall" id 0 as if it were still the
    # old, stripped "3-skill" id 0, and deleting it).
    classes_file = args.export / "classes.txt"
    shutil.copy2(classes_file, args.export / "classes_before_remap.txt")
    classes_file.write_text("\n".join(CANONICAL_CLASS_NAMES) + "\n")

    print(f"\nDone. Originals backed up to {args.export / 'labels_before_remap'} "
          f"and {args.export / 'classes_before_remap.txt'}.")
    print("classes.txt has been rewritten to the canonical order -- re-running "
          "this script now will correctly report 'no remapping needed'.")


if __name__ == "__main__":
    main()