"""🟡 Qwen-Image-Edit-2511 provider — Apache-2.0, 8-16 GB VRAM (GGUF/FP8).

Enaka varovalka kot FLUX: maska + hard restore izven maske.
"""
from __future__ import annotations

import time

import numpy as np

from ..config import settings
from ..util.images import hard_restore_outside
from .base import FinalizeRequest, FinalizeResult

_pipe = None
_tried = False


def _load():
    global _pipe, _tried
    if _tried:
        return _pipe
    _tried = True
    try:
        import torch
        from diffusers import QwenImageEditPlusPipeline
        dtype = {"bf16": torch.bfloat16, "fp16": torch.float16, "fp32": torch.float32}[settings.diffusion_dtype]
        _pipe = QwenImageEditPlusPipeline.from_pretrained(
            "Qwen/Qwen-Image-Edit-2511", torch_dtype=dtype)
        if settings.diffusion_device == "cuda":
            _pipe.enable_model_cpu_offload()
    except Exception as e:
        _pipe = None
        print(f"[qwen] ni na voljo: {e}")
    return _pipe


class QwenEditProvider:
    name = "QWEN_EDIT_2511"
    tier = "yellow"

    def available(self) -> bool:
        return _load() is not None

    def remove_object(self, image: np.ndarray, mask: np.ndarray) -> np.ndarray:
        from .lama import LamaProvider
        return LamaProvider().remove_object(image, mask)

    def finalize(self, req: FinalizeRequest) -> FinalizeResult:
        t0 = time.time()
        pipe = _load()
        if pipe is None:
            raise RuntimeError("Qwen-Image-Edit ni naložen. Uporabi 🟢 način.")
        from PIL import Image
        m = req.mask > 8
        out = pipe(
            image=[Image.fromarray(req.composite[..., :3]), Image.fromarray(req.product[..., :3])],
            prompt=req.prompt,
            num_inference_steps=max(4, req.steps),
        ).images[0]
        arr = np.asarray(out.convert("RGB"), dtype=np.uint8)
        alpha = (m.astype(np.float32) * req.strength)[..., None]
        blended = np.clip(arr * alpha + req.composite[..., :3] * (1 - alpha), 0, 255).astype(np.uint8)
        result = np.dstack([blended, req.composite[..., 3:]])
        result, _ = hard_restore_outside(req.scene, result, req.mask)
        return FinalizeResult(image=result, provider=self.name,
                              elapsed_ms=int((time.time() - t0) * 1000),
                              notes="multi-image refine + hard restore")
