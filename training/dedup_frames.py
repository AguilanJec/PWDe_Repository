"""
Drops near-duplicate frames from an FFmpeg extraction pass using
perceptual hashing (phash) — catches the runs of near-identical frames
that sparse sampling alone doesn't (e.g. a player idling in base, or a
paused loading screen), without needing exact pixel matches.

Non-destructive by default: near-duplicates are MOVED to a sibling
"duplicates" folder next to each source folder, not deleted, so you can
spot-check what got flagged before committing to it.

Expects the folder-per-video layout from the extraction step:
    extracted_frames/
      match1_1080x2400/
        frame_0001.jpg
        frame_0002.jpg
        ...
      match2_720x1600/
        ...

Usage:
    python dedup_frames.py --root extracted_frames
    python dedup_frames.py --root extracted_frames --threshold 8 --dry-run

How the threshold works:
    phash produces a 64-bit fingerprint per image. Hamming distance
    between two hashes is roughly "how different the images look" —
    0 = identical, 64 = maximally different. A frame is dropped as a
    near-duplicate of the last KEPT frame in its folder when their
    distance is <= threshold. Comparing against the last KEPT frame
    (not just the immediately preceding raw frame) is what actually
    collapses a long static run down to one representative frame,
    rather than just catching adjacent pairs.

    Default threshold=5 is conservative (only very similar frames get
    dropped). Raise it toward 10-12 if you're still seeing obvious
    near-duplicates survive; lower it if genuinely distinct moments are
    getting flagged as duplicates.
"""

import argparse
import shutil
from pathlib import Path

import imagehash
from PIL import Image

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def find_video_folders(root: Path):
    """Each immediate subfolder of root is treated as one video's frames."""
    return sorted(p for p in root.iterdir() if p.is_dir() and p.name != "duplicates")


def dedup_folder(folder: Path, threshold: int, dry_run: bool) -> tuple[int, int]:
    frames = sorted(
        p for p in folder.iterdir() if p.suffix.lower() in IMAGE_EXTENSIONS
    )
    if not frames:
        return 0, 0

    dup_folder = folder.parent / "duplicates" / folder.name
    if not dry_run:
        dup_folder.mkdir(parents=True, exist_ok=True)

    kept_count = 0
    dropped_count = 0
    last_kept_hash = None

    for frame_path in frames:
        try:
            with Image.open(frame_path) as img:
                current_hash = imagehash.phash(img)
        except Exception as e:  # noqa: BLE001
            print(f"  WARNING: could not read {frame_path.name} ({e}), keeping as-is")
            kept_count += 1
            continue

        if last_kept_hash is not None and (current_hash - last_kept_hash) <= threshold:
            dropped_count += 1
            if not dry_run:
                shutil.move(str(frame_path), str(dup_folder / frame_path.name))
        else:
            kept_count += 1
            last_kept_hash = current_hash

    return kept_count, dropped_count


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", required=True, type=Path,
                         help="Folder containing one subfolder per source video (the extraction step's output)")
    parser.add_argument("--threshold", type=int, default=5,
                         help="Max hamming distance to count as a duplicate (0-64, default 5)")
    parser.add_argument("--dry-run", action="store_true",
                         help="Report what would be dropped without moving any files")
    args = parser.parse_args()

    if not args.root.exists():
        print(f"Root folder not found: {args.root}")
        return

    video_folders = find_video_folders(args.root)
    if not video_folders:
        print(f"No subfolders found under {args.root} — expected one folder per source video.")
        return

    total_kept, total_dropped = 0, 0
    print(f"{'DRY RUN — ' if args.dry_run else ''}Deduping {len(video_folders)} folder(s) "
          f"with threshold={args.threshold}\n")

    for folder in video_folders:
        kept, dropped = dedup_folder(folder, args.threshold, args.dry_run)
        total_kept += kept
        total_dropped += dropped
        print(f"  {folder.name}: kept {kept}, dropped {dropped}")

    print(f"\nTotal: kept {total_kept}, dropped {total_dropped} "
          f"({total_dropped}/{total_kept + total_dropped} frames removed)")

    if not args.dry_run and total_dropped > 0:
        print(f"Dropped frames moved to {args.root}/duplicates/<video_name>/ — "
              f"review before deleting for good.")


if __name__ == "__main__":
    main()