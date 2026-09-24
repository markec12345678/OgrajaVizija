#!/usr/bin/env python3
"""
REALNI TESTI 1-5 (zahteva 15 in 16) — zaženi na stroju z GPU-jem (🟡 nivo).

Uporaba:
    python3 scripts/test_pipeline.py --scene scene.jpg --product ograja.jpg \
        --out out/ --provider FLUX2_KLEIN_4B

Za vsak par izpiše in zapiše:
    1) clean plate (odstranjena stara ograja),
    2) geometrijski rezultat (referenca + perspektiva + senca),
    3) AI finaliziran rezultat,
    4) METRIKO ZASCITE ORIGINALA: delez pikslov IZVEN maske, ki so se spremenili.
       Zahteva 16: ta stevila MORAJO biti 0 (oz. < 0.01 %), sicer je pipeline napacen.

Brez GPU-ja uporabi --provider GEOMETRY ali LAMA (🟢).
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.providers.flux2 import Flux2KleinProvider  # noqa: E402
from app.providers.geometry import GeometryProvider  # noqa: E402
from app.providers.lama import LamaProvider  # noqa: E402
from app.util import images as I  # noqa: E402


def homography_corners(w: int, h: int, quad):
    """quad = 4 normalizirane tocke (ZL, ZD, SD, SL) -> absolutne."""
    return [(x * w, y * h) for (x, y) in quad]


def polygon_mask(poly, w, h):
    import cv2
    m = np.zeros((h, w), np.uint8)
    pts = np.array([[(x * w, y * h) for (x, y) in poly]], dtype=np.int32)
    cv2.fillPoly(m, pts, 255)
    return m


def run_one(scene_path, product_path, out_dir, provider_name, placement):
    scene = I.imread_png_or_jpeg(Path(scene_path).read_bytes())
    product = I.imread_png_or_jpeg(Path(product_path).read_bytes())
    h, w = scene.shape[:2]

    mask = polygon_mask(placement["poly"], w, h)
    corners = homography_corners(w, h, placement["corners"])

    providers = {"GEOMETRY": GeometryProvider(), "LAMA": LamaProvider(),
                 "FLUX2_KLEIN_4B": Flux2KleinProvider()}
    prov = providers[provider_name]

    t0 = time.time()
    clean = prov.remove_object(scene, mask)
    t_clean = time.time() - t0

    # geometrijska sestava (enaka logika kot na telefonu, tu za referenco/test)
    composite = _warp_composite(clean, product, corners, mask)

    t0 = time.time()
    if provider_name == "GEOMETRY":
        final = composite
    else:
        from app.providers.base import FinalizeRequest
        req = FinalizeRequest(scene=scene, composite=composite, mask=mask, product=product,
                              prompt=("Replace the existing balcony railing with the supplied reference "
                                      "railing. Preserve the exact design, material, pattern, profiles, "
                                      "slats and proportions of the reference railing. Match perspective, "
                                      "lighting, shadows and reflections. Keep everything else unchanged."),
                              strength=0.35, steps=8, seed=1234)
        final = prov.finalize(req).image
    t_final = time.time() - t0

    # --- METRIKA ZASCITE ORIGINALA (zahteva 9/16)
    outside = mask < 8
    diff = np.abs(final[..., :3].astype(int) - scene[..., :3].astype(int)).max(axis=2)
    changed_outside = int((diff[outside] > 2).sum())
    total_outside = int(outside.sum())
    ratio = changed_outside / max(1, total_outside)

    name = Path(scene_path).stem
    I.imwrite_png(clean)[:0]  # noop
    (out_dir / f"{name}_clean.png").write_bytes(I.imwrite_png(clean))
    (out_dir / f"{name}_composite.png").write_bytes(I.imwrite_png(composite))
    (out_dir / f"{name}_final.png").write_bytes(I.imwrite_png(final))

    report = {
        "scene": str(scene_path), "product": str(product_path), "provider": provider_name,
        "clean_s": round(t_clean, 2), "final_s": round(t_final, 2),
        "changed_outside_mask": changed_outside,
        "outside_pixels": total_outside,
        "leakage_ratio": round(ratio, 6),
        "PASS_protection": ratio == 0.0,
    }
    (out_dir / f"{name}_report.json").write_text(json.dumps(report, indent=2))
    return report


def _warp_composite(clean, product, corners, mask):
    import cv2
    h, w = clean.shape[:2]
    ph, pw = product.shape[:2]
    src = np.float32([[0, 0], [pw, 0], [pw, ph], [0, ph]])
    dst = np.float32(corners)
    M = cv2.getPerspectiveTransform(src, dst)
    warped = cv2.warpPerspective(product, M, (w, h),
                                 flags=cv2.INTER_LINEAR,
                                 borderMode=cv2.BORDER_CONSTANT, borderValue=(0, 0, 0, 0))
    a = (warped[..., 3:4].astype(np.float32) / 255.0) * (mask[..., None] > 8)
    out = (warped[..., :3].astype(np.float32) * a + clean[..., :3].astype(np.float32) * (1 - a))
    return np.dstack([np.clip(out, 0, 255).astype(np.uint8), np.full((h, w, 1), 255, np.uint8)])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scene", required=True)
    ap.add_argument("--product", required=True)
    ap.add_argument("--placement", required=True, help="JSON: {'corners':[[x,y]x4],'poly':[[x,y]...]} normalizirano")
    ap.add_argument("--out", default="out")
    ap.add_argument("--provider", default="LAMA")
    args = ap.parse_args()

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    placement = json.loads(Path(args.placement).read_text())
    rep = run_one(args.scene, args.product, out, args.provider, placement)
    print(json.dumps(rep, indent=2))
    if not rep["PASS_protection"]:
        print("❌ ZASCITA ORIGINALA KRSENA — piksli izven maske so se spremenili!", file=sys.stderr)
        sys.exit(1)
    print("✅ zaščita originala držana (leakage = 0)")


if __name__ == "__main__":
    main()
