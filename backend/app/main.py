"""
OgrajaVizija backend — SAMOSTOJEN (zahteva 12).

Zagon:
    pip install -r requirements.txt
    uvicorn app.main:app --host 0.0.0.0 --port 8787

Brez Postgresa, Redisa, S3, JWT-ja, MLflow-a. Datoteke gredo na disk (data_dir).
AI je opcija prek providerjev; brez GPU-ja delujeta 🟢 GEOMETRY in 🟢 LAMA(Telea).
"""
from __future__ import annotations

import json
import os
import time
import uuid

import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

from .config import settings
from .providers.base import FinalizeRequest
from .providers.flux2 import Flux2KleinProvider
from .providers.geometry import GeometryProvider
from .providers.lama import LamaProvider
from .providers.qwen import QwenEditProvider
from .util import images as I

app = FastAPI(title="OgrajaVizija backend", version="0.2.0")
app.add_middleware(CORSMiddleware, allow_origins=settings.allow_origins,
                   allow_methods=["*"], allow_headers=["*"])

PROVIDERS = {
    p.name: p for p in [GeometryProvider(), LamaProvider(), Flux2KleinProvider(), QwenEditProvider()]
}
# 🔴 zunanji plačljivi API-ji so NAMERNO izklopljeni; odkleni jih samo sam:
if settings.replicate_api_token or settings.fal_key:
    raise RuntimeError(
        "Zunanji plačljivi API ključi so nastavljeni, a v tej različici backenda "
        "namerno ni 🔴 providerjev. Odstrani spremenljivki okolja.")


@app.get("/health")
def health():
    import platform
    gpu = ""
    try:
        import torch
        if torch.cuda.is_available():
            gpu = torch.cuda.get_device_name(0)
    except Exception:
        pass
    return {
        "ok": True,
        "version": app.version,
        "device": platform.system(),
        "gpu": gpu,
        "providers": [k for k, v in PROVIDERS.items() if v.available()],
        "python": platform.python_version(),
    }


@app.post("/remove-background")
async def remove_background(image: UploadFile = File(...), model: str = Form("birefnet")):
    """🟢/🟡 Izrez izdelka. BiRefNet (MIT), če je nameščen; sicer OpenCV GrabCut fallback."""
    data = await image.read()
    arr = I.imread_png_or_jpeg(data)
    try:
        out = _birefnet(arr)
    except Exception:
        out = _grabcut(arr)
    return Response(content=I.imwrite_png(out), media_type="image/png")


_birefnet = None


def _birefnet(arr: np.ndarray) -> np.ndarray:
    global _birefnet
    if _birefnet is None:
        from transformers import AutoModelForImageSegmentation  # MIT model
        _birefnet = AutoModelForImageSegmentation.from_pretrained("ZhengPeng7/BiRefNet", trust_remote_code=True)
        _birefnet.eval()
    import torch
    from PIL import Image
    from torchvision import transforms
    img = Image.fromarray(arr[..., :3]).convert("RGB")
    tf = transforms.Compose([
        transforms.Resize((1024, 1024)),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])
    x = tf(img).unsqueeze(0)
    with torch.no_grad():
        pred = _birefnet(x)[-1].sigmoid().cpu()
    mask = transforms.ToPILImage()(pred[0].squeeze()).resize(img.size)
    m = np.asarray(mask, dtype=np.uint8)
    out = arr.copy()
    out[..., 3] = m
    return out


def _grabcut(arr: np.ndarray) -> np.ndarray:
    import cv2
    rgb = arr[..., :3]
    mask = np.zeros(rgb.shape[:2], np.uint8)
    bgd = np.zeros((1, 65), np.float64); fgd = np.zeros((1, 65), np.float64)
    h, w = rgb.shape[:2]
    rect = (int(w * 0.05), int(h * 0.05), int(w * 0.9), int(h * 0.9))
    cv2.grabCut(rgb, mask, rect, bgd, fgd, 3, cv2.GC_INIT_WITH_RECT)
    m = np.where((mask == 2) | (mask == 0), 0, 255).astype(np.uint8)
    out = arr.copy(); out[..., 3] = m
    return out


@app.post("/remove")
async def remove(scene: UploadFile = File(...), mask: UploadFile = File(...),
                 provider: str = Form("LAMA")):
    """🟢 Odstrani staro ograjo -> clean plate."""
    img = I.imread_png_or_jpeg(await scene.read())
    m = I.mask_from_png(await mask.read(), img.shape[:2])
    p = PROVIDERS.get(provider, PROVIDERS["LAMA"])
    out = p.remove_object(img, m)
    return Response(content=I.imwrite_png(out), media_type="image/png")


@app.post("/segment")
async def segment(image: UploadFile = File(...), points: str = Form(""),
                  provider: str = Form("mobilesam")):
    """🟡 Segmentacija (MobileSAM/SAM3 na strežniku). Vrne PNG masko."""
    arr = I.imread_png_or_jpeg(await image.read())
    pts = [tuple(map(float, p.split(","))) for p in points.split(";") if p]
    try:
        m = _mobilesam(arr, pts)
    except Exception as e:
        raise HTTPException(503, f"segmentacija ni na voljo: {e}")
    return Response(content=I.imwrite_png(np.dstack([m, m, m, np.full_like(m, 255)])),
                    media_type="image/png")


_sam = None


def _mobilesam(arr: np.ndarray, pts):
    global _sam
    if _sam is None:
        from mobile_sam import sam_model_registry, SamPredictor  # Apache-2.0
        ckpt = os.path.join(settings.models_dir, "mobile_sam.pt")
        if not os.path.exists(ckpt):
            raise RuntimeError("manjka mobile_sam.pt")
        model = sam_model_registry["vit_t"](checkpoint=ckpt)
        _sam = SamPredictor(model)
    _sam.set_image(arr[..., :3])
    import numpy as _np
    input_points = _np.array(pts, dtype=float) if pts else None
    masks, _, _ = _sam.predict(
        point_coords=input_points if pts else None,
        point_labels=_np.ones(len(pts), dtype=int) if pts else None,
        multimask_output=False,
    )
    return (masks[0] * 255).astype(np.uint8)


@app.post("/finalize")
async def finalize(
    scene: UploadFile = File(...),
    composite: UploadFile = File(...),
    mask: UploadFile = File(...),
    product: UploadFile = File(...),
    request: str = Form("{}"),
):
    """
    🟡 AI finalizacija. Vrne multipart z 'meta' (JSON) in 'image' (PNG).

    Varnostna zahteva 9: po AI koraku se piksli IZVEN maske VRNEJO iz originala
    (hard_restore_outside), v 'meta' pa je changedOutsideMask, da lahko telefon
    (in testi) preverijo, da je 0.
    """
    req = json.loads(request or "{}")
    sc = I.imread_png_or_jpeg(await scene.read())
    comp = I.imread_png_or_jpeg(await composite.read())
    m = I.mask_from_png(await mask.read(), sc.shape[:2])
    pr = I.imread_png_or_jpeg(await product.read())

    name = req.get("provider", settings.default_provider)
    p = PROVIDERS.get(name)
    if p is None or not p.available():
        raise HTTPException(503, f"provider {name} ni na voljo na tem strežniku")

    fr = FinalizeRequest(
        scene=sc, composite=comp, mask=m, product=pr,
        prompt=req.get("prompt", ""),
        strength=float(req.get("strength", settings.diffusion_strength)),
        steps=int(req.get("steps", settings.diffusion_steps)),
        seed=int(req.get("seed", 1234)),
    )
    t0 = time.time()
    res = p.finalize(fr)
    fixed = I.hard_restore_outside(sc, res.image, m)
    res.image = fixed[0]
    outside = int(np.sum((np.abs(res.image[..., :3].astype(int) - sc[..., :3].astype(int))
                          .max(axis=2) > 2) & (m < 8)))
    meta = {
        "ok": True, "provider": res.provider,
        "elapsedMs": int((time.time() - t0) * 1000),
        "changedOutsideMask": outside,
        "message": res.notes,
    }
    body = b"--boundaryOgraja\r\n"
    body += b'Content-Disposition: form-data; name="meta"\r\nContent-Type: application/json\r\n\r\n'
    body += json.dumps(meta).encode() + b"\r\n"
    body += b"--boundaryOgraja\r\n"
    body += b'Content-Disposition: form-data; name="image"; filename="result.png"\r\nContent-Type: image/png\r\n\r\n'
    body += I.imwrite_png(res.image) + b"\r\n"
    body += b"--boundaryOgraja--\r\n"
    return Response(content=body, media_type="multipart/form-data; boundary=boundaryOgraja")


@app.get("/")
def root():
    return {"app": "OgrajaVizija backend", "docs": "/docs", "health": "/health"}
