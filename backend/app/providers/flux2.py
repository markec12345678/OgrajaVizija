"""🟡 FLUX.2 [klein] 4B provider — Apache-2.0, ~8-13 GB VRAM.

Pomembno za zahtevo 6/9/16: difuzija dobi ZE geometrijsko sestavljeno sliko
(composite) in masko, dela z NIZKO jakostjo (strength 0.25-0.45) in SAMO
znotraj maske. Po inferenci backend obvezno vrne piksle IZVEN maske iz
originala (hard_restore_outside), zato model fizicno ne more spremeniti
stavbe, oken, fasade ali okolice.
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
        from diffusers import Flux2KleinPipeline  # diffusers >= 0.37
        dtype = {"bf16": torch.bfloat16, "fp16": torch.float16, "fp32": torch.float32}[settings.diffusion_dtype]
        _pipe = Flux2KleinPipeline.from_pretrained(
            "black-forest-labs/FLUX.2-klein-4B", torch_dtype=dtype)
        if settings.diffusion_device == "cuda":
            _pipe.enable_model_cpu_offload()
        else:
            _pipe = _pipe.to("cpu")
    except Exception as e:  # brez GPU-ja / brez diffusers
        _pipe = None
        print(f"[flux2] ni na voljo: {e}")
    return _pipe


class Flux2KleinProvider:
    name = "FLUX2_KLEIN_4B"
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
            raise RuntimeError("FLUX.2 klein 4B ni naložen (manjka GPU/diffusers). Uporabi 🟢 način.")
        from PIL import Image
        m = req.mask > 8
        comp = Image.fromarray(req.composite[..., :3])
        out = pipe(
            prompt=req.prompt,
            image=comp,
            num_inference_steps=max(4, req.steps),
            guidance_scale=1.0,
            generator=__import__("torch").Generator("cuda").manual_seed(req.seed)
            if settings.diffusion_device == "cuda" else None,
        ).images[0]
        arr = np.asarray(out.convert("RGB"), dtype=np.uint8)
        # strength-blend znotraj maske: čim nižji strength, tem bolj ostane referenca
        alpha = (m.astype(np.float32) * req.strength)[..., None]
        blended = (arr.astype(np.float32) * alpha + req.composite[..., :3].astype(np.float32) * (1 - alpha))
        blended = np.clip(blended, 0, 255).astype(np.uint8)
        result = np.dstack([blended, req.composite[..., 3:]])
        result, _fixed = hard_restore_outside(req.scene, result, req.mask)
        return FinalizeResult(image=result, provider=self.name,
                              elapsed_ms=int((time.time() - t0) * 1000),
                              notes="masked low-strength refine + hard restore")
