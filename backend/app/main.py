"""
OgrajaVizija backend — SAMOSTOJEN (zahteva 12).

Zagon:
    pip install -r requirements.txt
    uvicorn app.main:app --host 0.0.0.0 --port 8787

Brez Postgresa, Redisa, S3, JWT-ja, MLflow-a. Datoteke gredo na disk (data_dir).
AI je opcija prek providerjev; brez GPU-ja delujeta 🟢 GEOMETRY in 🟢 LAMA(Telea).
"""
from __future__ import annotations

import hmac
import json
import os
import time
import uuid

import numpy as np
from fastapi import FastAPI, File, Form, Header, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, Response

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

# ---------------------------------------------------------------------------
# Roksal inquiry inbox — explicit bearer-token protected storage.
# The endpoint is disabled unless OVIZ_INQUIRY_TOKEN is configured.
# ---------------------------------------------------------------------------

INQUIRY_STATUSES = {
    "NEW",
    "ROKSAL_REVIEW",
    "SITE_MEASUREMENT",
    "OFFER_SENT",
    "ACCEPTED",
    "INSTALLATION",
    "COMPLETED",
    "CANCELLED",
}


def _require_inquiry_auth(authorization: str) -> None:
    expected = settings.inquiry_token.strip()
    if not expected:
        raise HTTPException(503, "Roksal inquiry inbox ni konfiguriran.")
    scheme, _, provided = authorization.partition(" ")
    if scheme.lower() != "bearer" or not provided.strip() or not hmac.compare_digest(provided.strip(), expected):
        raise HTTPException(401, "Neveljaven ali manjkajoč bearer token.")


def _inquiry_root() -> str:
    path = os.path.join(settings.data_dir, "inquiries")
    os.makedirs(path, exist_ok=True)
    return path


async def _save_inquiry_upload(upload: UploadFile | None, path: str, current_total: int) -> int:
    if upload is None or not upload.filename:
        return current_total
    allowed = {".jpg", ".jpeg", ".png", ".webp"}
    ext = os.path.splitext(upload.filename or "")[1].lower()
    if ext not in allowed:
        raise HTTPException(400, f"Nepodprta pripona datoteke: {ext or 'brez'}")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    total = current_total
    with open(path, "wb") as out:
        while True:
            chunk = await upload.read(1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > settings.inquiry_max_bytes:
                out.close()
                try:
                    os.remove(path)
                except FileNotFoundError:
                    pass
                raise HTTPException(413, "Skupna velikost priponk presega dovoljeno omejitev 25 MB.")
            out.write(chunk)
    return total


@app.post("/inquiries")
async def create_inquiry(
    payload: str = Form("{}"),
    original: UploadFile | None = File(None),
    result: UploadFile | None = File(None),
    product: UploadFile | None = File(None),
    authorization: str = Header(""),
):
    _require_inquiry_auth(authorization)
    try:
        data = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise HTTPException(400, f"Neveljaven JSON payload: {exc.msg}") from exc

    project = data.get("project")
    if len(payload.encode("utf-8")) > settings.inquiry_max_bytes:
        raise HTTPException(413, "Payload presega dovoljeno omejitev 25 MB.")
    if not isinstance(project, dict) or not project.get("id"):
        raise HTTPException(400, "Payload ne vsebuje veljavnega projekta.")
    inquiry_id = "inq_" + uuid.uuid4().hex
    received_at = int(time.time() * 1000)
    directory = os.path.join(_inquiry_root(), inquiry_id)
    os.makedirs(directory, exist_ok=False)
    meta = {
        "id": inquiry_id,
        "receivedAt": received_at,
        "status": "NEW",
        "projectId": str(project.get("id", "")),
        "projectName": str(project.get("name", "")),
        "category": str((project.get("config") or {}).get("category", "")),
        "customerName": str((project.get("config") or {}).get("customerName", "")),
    }
    total = 0
    try:
        with open(os.path.join(directory, "payload.json"), "w", encoding="utf-8") as out:
            json.dump({
                "receivedAt": received_at,
                "status": "NEW",
                "project": project,
                "inquiryText": str(data.get("inquiryText", "")),
            }, out, ensure_ascii=False, indent=2)
        for upload in (original, result, product):
            if upload is not None and upload.filename:
                safe_name = {"original": "original", "result": "result", "product": "product"}.get(
                    os.path.splitext(upload.filename)[0].lower()
                )
                if safe_name:
                    total = await _save_inquiry_upload(
                        upload,
                        os.path.join(directory, safe_name + os.path.splitext(upload.filename)[1].lower()),
                        total,
                    )
        meta["attachmentBytes"] = total
        with open(os.path.join(directory, "meta.json"), "w", encoding="utf-8") as out:
            json.dump(meta, out, ensure_ascii=False, indent=2)
    except Exception:
        import shutil
        shutil.rmtree(directory, ignore_errors=True)
        raise

    return {
        "ok": True,
        "inquiryId": inquiry_id,
        "status": "NEW",
        "receivedAt": received_at,
        "message": "Povpraševanje je shranjeno v lokalni Roksal inbox.",
    }


@app.get("/inquiries/health")
def inquiry_health(authorization: str = Header("")):
    _require_inquiry_auth(authorization)
    root = _inquiry_root()
    records = []
    for entry in os.listdir(root):
        meta_path = os.path.join(root, entry, "meta.json")
        if os.path.isfile(meta_path):
            try:
                records.append(json.load(open(meta_path, encoding="utf-8")))
            except Exception:
                continue
    records.sort(key=lambda x: x.get("receivedAt", 0), reverse=True)
    return {
        "ok": True,
        "total": len(records),
        "lastStatus": records[0].get("status", "") if records else "",
    }


@app.get("/inquiries")
def list_inquiries(authorization: str = Header("")):
    _require_inquiry_auth(authorization)
    root = _inquiry_root()
    out = []
    for entry in os.listdir(root):
        meta_path = os.path.join(root, entry, "meta.json")
        if not os.path.isfile(meta_path):
            continue
        try:
            out.append(json.load(open(meta_path, encoding="utf-8")))
        except Exception:
            pass
    return sorted(out, key=lambda x: x.get("receivedAt", 0), reverse=True)


@app.get("/inquiries/{inquiry_id}")
def get_inquiry(inquiry_id: str, authorization: str = Header("")):
    _require_inquiry_auth(authorization)
    directory = os.path.join(_inquiry_root(), inquiry_id)
    payload_path = os.path.join(directory, "payload.json")
    if not os.path.isfile(payload_path):
        raise HTTPException(404, "Povpraševanje ne obstaja.")
    with open(payload_path, encoding="utf-8") as fh:
        payload = json.load(fh)
    meta_path = os.path.join(directory, "meta.json")
    with open(meta_path, encoding="utf-8") as fh:
        meta = json.load(fh)
    payload["meta"] = meta
    payload["attachments"] = sorted(
        name for name in os.listdir(directory) if name not in {"payload.json", "meta.json"}
    )
    return payload


@app.get("/inquiries/{inquiry_id}/files/{filename}")
def get_inquiry_file(
    inquiry_id: str,
    filename: str,
    authorization: str = Header(""),
):
    _require_inquiry_auth(authorization)
    allowed = {"original.jpg", "original.jpeg", "original.png", "original.webp",
               "result.jpg", "result.jpeg", "result.png", "result.webp",
               "product.jpg", "product.jpeg", "product.png", "product.webp"}
    safe_name = os.path.basename(filename).lower()
    if safe_name not in allowed:
        raise HTTPException(400, "Nepodprta priponka.")
    path = os.path.join(_inquiry_root(), inquiry_id, safe_name)
    if not os.path.isfile(path):
        raise HTTPException(404, "Priponka ne obstaja.")
    return FileResponse(path)


@app.patch("/inquiries/{inquiry_id}")
async def update_inquiry(
    inquiry_id: str,
    request: Request,
    authorization: str = Header(""),
):
    _require_inquiry_auth(authorization)
    directory = os.path.join(_inquiry_root(), inquiry_id)
    meta_path = os.path.join(directory, "meta.json")
    if not os.path.isfile(meta_path):
        raise HTTPException(404, "Povpraševanje ne obstaja.")
    body = await request.json()
    status = str(body.get("status", "")).strip()
    if status not in INQUIRY_STATUSES:
        raise HTTPException(400, "Neveljaven status.")
    with open(meta_path, encoding="utf-8") as fh:
        meta = json.load(fh)
    meta["status"] = status
    with open(meta_path, "w", encoding="utf-8") as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=2)
    payload_path = os.path.join(directory, "payload.json")
    if os.path.isfile(payload_path):
        with open(payload_path, encoding="utf-8") as fh:
            payload = json.load(fh)
        payload["status"] = status
        with open(payload_path, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=2)
    return {"ok": True, "inquiryId": inquiry_id, "status": status}
