"""Pomozne funkcije za slike: branje/pisanje, maska, hard-composite (zascita originala)."""
from __future__ import annotations

import io
import numpy as np
from PIL import Image


def imread_png_or_jpeg(data: bytes) -> np.ndarray:
    """Vrne HxWx4 uint8 (RGBA)."""
    img = Image.open(io.BytesIO(data)).convert("RGBA")
    return np.asarray(img, dtype=np.uint8)


def imwrite_png(arr: np.ndarray) -> bytes:
    buf = io.BytesIO()
    Image.fromarray(arr.astype(np.uint8), mode="RGBA").save(buf, format="PNG")
    return buf.getvalue()


def imwrite_jpeg(arr: np.ndarray, quality: int = 94) -> bytes:
    buf = io.BytesIO()
    rgb = arr[..., :3] if arr.shape[-1] == 4 else arr
    Image.fromarray(rgb.astype(np.uint8), mode="RGB").save(buf, format="JPEG", quality=quality)
    return buf.getvalue()


def mask_from_png(data: bytes, target_hw: tuple[int, int] | None = None) -> np.ndarray:
    """Siva/alfa maska -> HxW uint8 (0..255)."""
    img = Image.open(io.BytesIO(data)).convert("L")
    m = np.asarray(img, dtype=np.uint8)
    if target_hw is not None and m.shape != target_hw:
        m = np.asarray(Image.fromarray(m).resize((target_hw[1], target_hw[0]), Image.NEAREST), dtype=np.uint8)
    return m


def feather(mask: np.ndarray, radius: int) -> np.ndarray:
    if radius <= 0:
        return mask
    import cv2
    k = radius * 2 + 1
    return cv2.GaussianBlur(mask, (k, k), 0)


def hard_restore_outside(original: np.ndarray, result: np.ndarray, mask: np.ndarray,
                         feather_px: int = 4) -> tuple[np.ndarray, int]:
    """
    ZAHTEVA 9: vse piksle IZVEN maske prepise iz originala.
    Vrne (rezultat, stevilo popravljenih pikslov zunaj maske).
    """
    m = feather(mask, feather_px).astype(np.float32) / 255.0
    m3 = m[..., None]
    out = (result.astype(np.float32) * m3 + original.astype(np.float32) * (1.0 - m3))
    out = np.clip(out, 0, 255).astype(np.uint8)
    fixed = int(np.sum(mask < 8))
    return out, fixed
