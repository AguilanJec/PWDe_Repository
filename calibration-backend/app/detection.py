"""
YOLOv8 inference on onnxruntime — no torch/ultralytics in the deployed image.

Reproduces the pre/post-processing ultralytics does for detection:
letterbox to a square input, run the model, then confidence filter +
per-class NMS, and map boxes back to the original image's pixel space.
"""

from dataclasses import dataclass

import numpy as np
import onnxruntime as ort
from PIL import Image

from .constants import CLASS_NAMES

LETTERBOX_FILL = (114, 114, 114)  # ultralytics' padding colour


@dataclass
class Detection:
    class_id: int
    class_name: str
    confidence: float
    box: tuple[float, float, float, float]  # x1, y1, x2, y2 in original image pixels


class YOLOv8Detector:
    def __init__(self, weights_path: str, conf_threshold: float = 0.25,
                 iou_threshold: float = 0.45):
        opts = ort.SessionOptions()
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(weights_path, opts,
                                            providers=["CPUExecutionProvider"])
        inp = self.session.get_inputs()[0]
        self.input_name = inp.name
        self.imgsz = int(inp.shape[2])  # NCHW, square
        self.conf_threshold = conf_threshold
        self.iou_threshold = iou_threshold

        n_out_classes = self.session.get_outputs()[0].shape[1] - 4
        if n_out_classes != len(CLASS_NAMES):
            raise ValueError(f"Model has {n_out_classes} classes but CLASS_NAMES "
                             f"has {len(CLASS_NAMES)} — they must match.")

    def _letterbox(self, img: Image.Image) -> tuple[np.ndarray, float, tuple[int, int]]:
        w, h = img.size
        scale = min(self.imgsz / w, self.imgsz / h)
        nw, nh = round(w * scale), round(h * scale)
        pad_x, pad_y = (self.imgsz - nw) // 2, (self.imgsz - nh) // 2
        canvas = Image.new("RGB", (self.imgsz, self.imgsz), LETTERBOX_FILL)
        canvas.paste(img.resize((nw, nh), Image.BILINEAR), (pad_x, pad_y))
        arr = np.asarray(canvas, dtype=np.float32) / 255.0
        return arr.transpose(2, 0, 1)[None], scale, (pad_x, pad_y)

    def detect(self, img: Image.Image, conf_threshold: float | None = None) -> list[Detection]:
        conf_threshold = self.conf_threshold if conf_threshold is None else conf_threshold
        img = img.convert("RGB")
        tensor, scale, (pad_x, pad_y) = self._letterbox(img)
        # Output: (1, 4 + nc, N) — rows are cx, cy, w, h, then per-class scores.
        preds = self.session.run(None, {self.input_name: tensor})[0][0].T

        scores = preds[:, 4:]
        class_ids = scores.argmax(axis=1)
        confs = scores[np.arange(len(scores)), class_ids]
        keep = confs >= conf_threshold
        if not keep.any():
            return []
        preds, class_ids, confs = preds[keep], class_ids[keep], confs[keep]

        cx, cy, bw, bh = preds[:, 0], preds[:, 1], preds[:, 2], preds[:, 3]
        boxes = np.stack([cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2], axis=1)
        boxes -= [pad_x, pad_y, pad_x, pad_y]
        boxes /= scale
        w, h = img.size
        boxes[:, [0, 2]] = boxes[:, [0, 2]].clip(0, w)
        boxes[:, [1, 3]] = boxes[:, [1, 3]].clip(0, h)

        results = []
        for i in _nms_per_class(boxes, confs, class_ids, self.iou_threshold):
            cid = int(class_ids[i])
            results.append(Detection(
                class_id=cid,
                class_name=CLASS_NAMES[cid],
                confidence=round(float(confs[i]), 4),
                box=tuple(round(float(v), 1) for v in boxes[i]),
            ))
        return results


def _nms_per_class(boxes: np.ndarray, scores: np.ndarray, class_ids: np.ndarray,
                   iou_threshold: float) -> list[int]:
    # Offset boxes by class so boxes of different classes never overlap —
    # lets a single NMS pass behave like per-class NMS (same trick as torchvision).
    offset = class_ids[:, None].astype(np.float32) * (boxes.max() + 1)
    b = boxes + offset
    areas = (b[:, 2] - b[:, 0]) * (b[:, 3] - b[:, 1])
    order = scores.argsort()[::-1]
    keep = []
    while order.size:
        i = order[0]
        keep.append(int(i))
        rest = order[1:]
        xx1 = np.maximum(b[i, 0], b[rest, 0])
        yy1 = np.maximum(b[i, 1], b[rest, 1])
        xx2 = np.minimum(b[i, 2], b[rest, 2])
        yy2 = np.minimum(b[i, 3], b[rest, 3])
        inter = np.clip(xx2 - xx1, 0, None) * np.clip(yy2 - yy1, 0, None)
        iou = inter / (areas[i] + areas[rest] - inter + 1e-9)
        order = rest[iou <= iou_threshold]
    return keep
