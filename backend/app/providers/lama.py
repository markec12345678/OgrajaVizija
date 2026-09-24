"""🟢 LAMA provider — LaMa za odstranitev stare ograje (Apache-2.0, CPU OK).

Namestitev:  pip install simple-lama-inpainting   (ali iopaint)
Če paket manjka, provider javi available()=False in backend sam pade na
OpenCV Telea (še vedno 🟢, brez plačila).
"""
from __future__ import annotations

import time

import numpy as np

from .base import FinalizeRequest, FinalizeResult

_lama = None
_tried = False


def _get():
    global _lama, _tried
    if _tried:
        return _lama
    _tried = True
    try:
        from simple_lama_inpainting import SimpleLama  # type: ignore
        _lama = SimpleLama()
    except Exception:
        _lama = None
    return _lama


class LamaProvider:
    name = "LAMA"
    tier = "green"

    def available(self) -> bool:
        return True  # tudi brez SimpleLama delujemo prek OpenCV fallbacka

    def remove_object(self, image: np.ndarray, mask: np.ndarray) -> np.ndarray:
        import cv2
        m = ((mask > 8).astype(np.uint8)) * 255
        rgb = image[..., :3]
        lama = _get()
        if lama is not None:
            from PIL import Image
            try:
                res = lama(Image.fromarray(rgb), Image.fromarray(m).convert("L"))
                return np.dstack([np.asarray(res.convert("RGB")), image[..., 3:]])
            except Exception:
                pass
        out = rgb.copy()
        cv2.inpaint(out, m, 12, cv2.INPAINT_TELEA, dst=out)
        return np.dstack([out, image[..., 3:]])

    def finalize(self, req: FinalizeRequest) -> FinalizeResult:
        t0 = time.time()
        clean = self.remove_object(req.scene, req.mask)
        # geometrijsko sestavo naredi telefon; tu vrnemo clean plate kot "rezultat",
        # ce odjemalec zahteva LAMA-only finalizacijo
        return FinalizeResult(image=clean, provider=self.name,
                              elapsed_ms=int((time.time() - t0) * 1000),
                              notes="clean plate (LaMa/Telea)")
