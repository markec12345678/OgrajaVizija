# 02 · Arhitektura

## Princip: geometrija vstavlja, AI samo finalizira

```
fotografija izdelka ──(BiRefNet/Otsu)──▶ RGBA izrez ──┐
                                                      │  homografija (4 vogali)
fotografija balkona ──(maska)──▶ LaMa clean plate ───▶├──▶ geometrijska sestava 🟢
                                                      │    + LAB svetlobno ujemanje
                                                      │    + senca stika
                                                      ▼
                                       [opcijsko 🟡] difuzija z strength 0.25–0.45
                                                      SAMO znotraj maske
                                                      ▼
                                       HARD-RESTORE: piksli IZVEN maske = original
                                                      ▼
                                       rezultat + metrika changedOutsideMask (mora biti 0)
```

Zakaj ne obratno (difuzija vstavlja)? Ker noben difuzijski model ne more zagotoviti
pikselne identitete vzorca letvic (zahteva 6 in 16). Geometrija pa lahko.

## Android (Kotlin + Compose)

| Plast | Datoteke | Odgovornost |
|---|---|---|
| UI | `ui/screens/*` | 6 korakov, brez tehničnih nastavitev (zahteva 14) |
| Gestno | `ui/components/ZoomPanBox.kt` | zoom/pan (2 prsta), risanje/vogali (1 prst), koordinate slike |
| Domain | `data/ProjectController.kt` | koraki 1–6, render local/server, shranjevanje |
| Shramba | `data/ProjectStore.kt` | `projects/<id>/{project.json, original.jpg, product.jpg, cutout.png, mask.png, result.jpg, variants/*}` |
| Jedro | `imaging/PureCore.kt`, `Homography.kt` | čisti Kotlin (JVM-testabilno): warp, composite, LAB, senca, inpaint, Otsu, metrike |
| Maska | `imaging/MaskEditor.kt` | čopič +/−, pravokotnik, flood fill, undo/redo (14 stopenj) |
| Segmentacija | `segmentation/OnDeviceSegmenter.kt` | MediaPipe Interactive Segmenter (model se prenese ob prvem tapu) |
| Omrežje | `network/ApiClient.kt` | multipart REST; brez Retrofit/OkHttp (manj odvisnosti) |

Odvisnosti: Compose BOM 2024.12.01, CameraX 1.4.1, MediaPipe tasks-vision **1.0.0**
(nov Stroke API), onnxruntime-android 1.26.0 (pripravljen za BiRefNet-lite ONNX),
kotlinx-serialization, Coil.

## Backend (FastAPI, samostojen)

* **Brez** Postgresa, Redisa, S3, JWT, MLflow, Dockerja (zahteva 12: samostojen zagon).
* `/health` · `/remove-background` · `/remove` · `/segment` · `/finalize`
* Providerji: `GEOMETRY` 🟢, `LAMA` 🟢 (SimpleLama ali OpenCV Telea fallback),
  `FLUX2_KLEIN_4B` 🟡, `QWEN_EDIT_2511` 🟡.
* `/finalize` vrača multipart (`meta` JSON + `image` PNG); `meta.changedOutsideMask`
  omogoča telefonu in testom, da **preverijo** zaščito originala.
* 🔴 zunanji plačljivi API: če sta nastavljena `REPLICATE_API_TOKEN` ali `FAL_KEY`,
  se backend **namerno ne zažene** — plačljiva pot ni del izdelka.

## Zaščita originala (zahteva 9) — tri varovalke

1. Telefon: `PureCore.composite(..., maskPx=...)` — piksli izven maske se prisilno
   prepišejo z originalom (`if (!inMask) cur = orig`).
2. Backend: `hard_restore_outside()` po AI koraku (feather 4 px na robu maske).
3. Test: `leakageRatio` / `changedOutsideMask` mora biti 0, sicer test pade (CI + `test_pipeline.py`).
