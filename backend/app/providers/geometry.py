"""🟢 GEOMETRY provider — brez AI. Samo deterministična sestava (rezerva/test)."""
from __future__ import annotations

import time

import numpy as np

from .base import FinalizeRequest, FinalizeResult


class GeometryProvider:
    name = "GEOMETRY"
    tier = "green"

    def available(self) -> bool:
        return True

    def remove_object(self, image: np.ndarray, mask: np.ndarray) -> np.ndarray:
        # preprosto piramidno zapolnjevanje (enako kot na telefonu)
        import cv2
        m = (mask > 8).astype(np.uint8)
        out = image.copy()
        cv2.inpaint(out[..., :3], m * 255, 12, cv2.INPAINT_TELEA, dst=out[..., :3])
        return out

    def finalize(self, req: FinalizeRequest) -> FinalizeResult:
        t0 = time.time()
        return FinalizeResult(image=req.composite, provider=self.name,
                              elapsed_ms=int((time.time() - t0) * 1000),
                              notes="deterministična geometrija, brez AI")
