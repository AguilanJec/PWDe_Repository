#!/bin/bash
#
# extract_frames.sh
#
# Extracts frames from clashroyale_*.mp4 videos in raw_footage/ at a rate
# of 1 frame per 2 seconds (fps=0.5), storing each video's frames in its
# own folder under extracted_frames/.
#
# Usage:
#   ./extract_frames.sh
#
# Run this from the directory that contains raw_footage/ and
# extracted_frames/ (e.g. training/clash_royale_dataset).

set -euo pipefail

RAW_DIR="raw_footage"
OUT_DIR="extracted_frames"
FPS="0.5"          # 1 frame every 2 seconds
PATTERN="clashroyale_*.mp4"

if [ ! -d "$RAW_DIR" ]; then
    echo "Error: '$RAW_DIR' directory not found. Run this script from the dataset root." >&2
    exit 1
fi

shopt -s nullglob
videos=("$RAW_DIR"/$PATTERN)

if [ ${#videos[@]} -eq 0 ]; then
    echo "No videos matching '$PATTERN' found in '$RAW_DIR'." >&2
    exit 1
fi

for video in "${videos[@]}"; do
    name=$(basename "$video" .mp4)
    outdir="$OUT_DIR/$name"
    mkdir -p "$outdir"

    echo "Extracting frames from '$video' -> '$outdir' (fps=$FPS)..."
    ffmpeg -y -i "$video" -vf "fps=$FPS" "$outdir/${name}_%04d.png"
done

echo "Done. Frames extracted for ${#videos[@]} video(s)."