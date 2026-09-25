"""
Runs a trained YOLOv8 checkpoint over the tasks ALREADY in a Label
Studio project that don't have an annotation yet, and attaches the
boxes to those tasks as predictions via the REST API. Opening one of
those tasks then shows the model's boxes pre-filled, so reviewing
means correcting boxes instead of drawing every one from scratch.

This is the counterpart to export_predictions_for_label_studio.py:
that script creates NEW tasks from a folder of frames, which would
duplicate frames that were already imported as tasks. Use this one
when the frames are already in the project (e.g. Clash Royale, where
every frame was imported up front and only some got labeled).

Auth: set LABEL_STUDIO_TOKEN to a Personal Access Token (Account &
Settings -> Personal Access Token). That token is a JWT refresh token,
so it's exchanged for a short-lived access token here. Legacy tokens
(plain hex) are sent as-is, if the instance still accepts them.

Image paths: each task's "image" URL is expected to look like
/data/local-files/?d=<storage folder>/<clip>/<filename>, and
<clip>/<filename> is looked up under --frames-root (the local folder
mounted as that storage folder in the Label Studio container).

Usage:
    set LABEL_STUDIO_TOKEN=<token>
    python push_predictions_to_label_studio.py \\
        --weights ../runs/detect/clash_royale_v1/weights/best.pt \\
        --project-id 4 \\
        --frames-root extracted_frames \\
        --model-version clash_royale_v1

    # See what would be pushed without writing anything:
    python push_predictions_to_label_studio.py ... --dry-run
"""

import argparse
import os
import re
from pathlib import Path
from urllib.parse import unquote

import requests
from ultralytics import YOLO

from export_predictions_for_label_studio import build_prediction

IMAGE_PATH_RE = re.compile(r"[?&]d=[^/]+/([^&]+)")


class LabelStudio:
    def __init__(self, url: str, token: str):
        self.url = url.rstrip("/")
        self.token = token
        self.session = requests.Session()
        self._authenticate()

    def _authenticate(self):
        if token_is_jwt(self.token):
            resp = self.session.post(f"{self.url}/api/token/refresh",
                                     json={"refresh": self.token})
            resp.raise_for_status()
            self.session.headers["Authorization"] = f"Bearer {resp.json()['access']}"
        else:
            self.session.headers["Authorization"] = f"Token {self.token}"

    def request(self, method: str, path: str, **kwargs) -> requests.Response:
        """Access tokens are short-lived, so a long prediction run can
        outlast one -- re-authenticate once on 401 and retry."""
        resp = self.session.request(method, f"{self.url}{path}", **kwargs)
        if resp.status_code == 401 and token_is_jwt(self.token):
            self._authenticate()
            resp = self.session.request(method, f"{self.url}{path}", **kwargs)
        resp.raise_for_status()
        return resp

    def iter_tasks(self, project_id: int):
        page = 1
        while True:
            resp = self.session.get(f"{self.url}/api/tasks",
                                    params={"project": project_id, "page": page,
                                            "page_size": 100})
            if resp.status_code == 404:  # past the last page
                return
            resp.raise_for_status()
            body = resp.json()
            tasks = body["tasks"] if isinstance(body, dict) else body
            if not tasks:
                return
            yield from tasks
            page += 1


def token_is_jwt(token: str) -> bool:
    return token.count(".") == 2


def resolve_image(task: dict, frames_root: Path) -> Path | None:
    match = IMAGE_PATH_RE.search(task["data"].get("image", ""))
    if not match:
        return None
    return frames_root / unquote(match.group(1))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--weights", required=True, type=Path)
    parser.add_argument("--project-id", required=True, type=int)
    parser.add_argument("--frames-root", required=True, type=Path,
                         help="Local folder mounted as the project's local-files "
                              "storage (e.g. extracted_frames/)")
    parser.add_argument("--url", default="http://localhost:8080")
    parser.add_argument("--conf", type=float, default=0.25,
                         help="Confidence threshold. Kept low on purpose: deleting a "
                              "wrong box is cheaper than drawing a missed one.")
    parser.add_argument("--model-version", default=None,
                         help="Shown in Label Studio next to each prediction "
                              "(default: the weights' run folder name)")
    parser.add_argument("--include-predicted", action="store_true",
                         help="Also predict tasks that already have a prediction "
                              "(default: skip them, so re-runs don't stack duplicates)")
    parser.add_argument("--dry-run", action="store_true",
                         help="Run the model and report, but don't push anything")
    args = parser.parse_args()

    token = os.environ.get("LABEL_STUDIO_TOKEN")
    if not token:
        parser.error("set LABEL_STUDIO_TOKEN to a Label Studio personal access token")

    model_version = args.model_version or args.weights.resolve().parent.parent.name
    ls = LabelStudio(args.url, token)

    all_tasks = list(ls.iter_tasks(args.project_id))
    todo = [t for t in all_tasks
            if not t.get("total_annotations")
            and (args.include_predicted or not t.get("total_predictions"))]
    print(f"{len(all_tasks)} tasks in project {args.project_id}, "
          f"{len(todo)} unlabeled{'' if args.include_predicted else ' and unpredicted'} "
          f"to predict.")
    if not todo:
        return

    model = YOLO(str(args.weights))
    class_names = model.names  # {id: name}, from the checkpoint itself

    pushed, total_boxes, missing = 0, 0, []
    for task in todo:
        image_path = resolve_image(task, args.frames_root)
        if image_path is None or not image_path.exists():
            missing.append(task["id"])
            continue

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

        prediction = build_prediction(image_path, boxes, model_version)
        if not args.dry_run:
            ls.request("POST", "/api/predictions", json={"task": task["id"], **prediction})
        pushed += 1

    avg = total_boxes / pushed if pushed else 0
    verb = "Would push" if args.dry_run else "Pushed"
    print(f"{verb} predictions for {pushed} tasks, {total_boxes} boxes "
          f"({avg:.1f} avg/image), model_version={model_version!r}.")
    if missing:
        print(f"WARNING: {len(missing)} tasks skipped, image not found under "
              f"{args.frames_root}: task ids {missing[:10]}"
              f"{' ...' if len(missing) > 10 else ''}")


if __name__ == "__main__":
    main()
