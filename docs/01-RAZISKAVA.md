# 01 · Raziskava GitHub projektov in modelov

**Datum analize:** 23. 9. 2026
**Metoda:** vsi podatki so preverjeni prek GitHub API-ja in HuggingFace API-ja (zvezdice, zadnji commit, arhivirano, licenca, velikost, jeziki, drevo datotek) + branje dejanske izvorne kode kloniranega repoja. Nič ni prevzeto »na pamet«.

---

## 1. Predlagana osnova: `nazarpalamarenkoo-ui/AI-Photo-Object-Editor`

Kloniran v `research/base/` in prebran.

| Lastnost | Vrednost (preverjeno) |
|---|---|
| Repo | https://github.com/nazarpalamarenkoo-ui/AI-Photo-Object-Editor |
| **Licenca** | **MIT** ✅ (komercialno dovoljeno, treba ohraniti avtorstvo) |
| Ustvarjen | 7. 3. 2026 |
| Zadnji push | **15. 8. 2026** (≈ 5 tednov pred analizo — projekt ni opuščen, a ni dneveno aktiven) |
| Zvezdice / forki | **0 / 0** → samostojen (portfeljski) projekt, brez skupnosti |
| Velikost | 1,6 MB, 413 datotek |
| Jeziki | Python 1,63 MB · TypeScript 285 kB · Vue 83 kB · CSS 41 kB · Shell · Dockerfile |
| Odvisnosti | torch 2.5.1 (CUDA 12.4), diffusers 0.29.1, transformers, ultralytics, MobileSAM (git), iopaint (LaMa), rembg[cpu] 2.0.57, opencv 4.9, FastAPI, ARQ, Redis, PostgreSQL, aioboto3 (S3/R2), MLflow, OpenTelemetry, Prometheus |
| Oblak/API | **Ne** za AI (vse lokalno), **da** za infrastrukturo: PostgreSQL, Redis, S3/R2, MLflow |
| Android | ❌ **nič** — odjemalec je Vue 3 SPA |
| Koda ML | `backend/app/ml/` + `backend/app/services/ml/` = **9.317 vrstic** |

### Kaj projekt že ima (in je res dobro)

1. **Tri ločene poti urejanja** (`docs/ML_PIPELINE.md`):
   - A: YOLO detekcija → LaMa (odstranitev ali «paste» zamenjava bbox-a)
   - B: MobileSAM segmentacija → LaMa
   - C: MobileSAM segmentacija → **difuzijska zamenjava z referenčno sliko** (SD inpainting + IP-Adapter)
2. **MobileSAM segmentacija v 4 načinih**: automatic (segment everything), prompted (bbox), polygon (prosto roko + feathering), hybrid (YOLO semena → MobileSAM v enem batch passu).
3. **Odstranjevanje objektov** prek `iopaint` (LaMa) — dokazano dobra izbira, deluje tudi na CPU.
4. **Procesorji**, ki jih lahko vzamemo 1:1: `background_remover.py` (rembg / GrabCut / Otsu), `color_matcher.py` (mean-std, histogram, LAB color transfer), `edge_blender.py`, `image_compositor.py`, `polygon_mask.py`.
5. **Async job pattern**: sinhroni endpoint + `/async` varianta + job-status polling (ARQ worker). To je točno tisto, kar potrebuje mobilni odjemalec za dolgotrajne AI klice.
6. **Verzioniranje slik** (undo/redo/save/reset) in **knjižnica izrezov** (asset library) — ujema se z zahtevo 10 (več variant).
7. **MLflow + OpenTelemetry** za sledenje vsaki inferenci — uporabno za testiranje kakovosti (zahteva 16).

### Kaj manjka glede na tvoj cilj (kritično)

| # | Zahteva iz specifikacije | Stanje v repoju | Ocena |
|---|---|---|---|
| 7 | **Perspektiva / 4 vogali / homografija** | `image_compositor.compose()` naredi `clean_bg.paste(replacement, (x1, y1))` — **lepljenje pravokotnika na bbox**. Ni homografije, ni warp-a, ni vogalov. | 🔴 **manjka v celoti** |
| 6, 16 | **Ohranjanje točnega videza izdelka** | Pot C = `stable-diffusion-v1-5/stable-diffusion-inpainting` + `h94/IP-Adapter` (`ip-adapter-plus_sd15.bin`, scale 0.8) | 🔴 **napačna generacija modela** — IP-Adapter prenaša *koncept*, ne pikslov. Model bo ograjo **prerisal**. |
| 6, 8 | Sence, osvetlitev, odsevi | nič (samo `color_matcher` in `edge_blender`) | 🔴 manjka |
| 9 | Zaščita originala | Delno: `DiffusionReplacer` sicer crop→upscale→downscale→feather-blend nazaj, kar je dober princip, a ni **verifikacije** (nič ne preverja, ali so piksli zunaj maske ostali enaki) | 🟡 princip da, dokazila ne |
| 2, 14 | Android aplikacija | nič | 🔴 manjka |
| 12 | Samostojen backend | `docker-compose.yml` ima **11 storitev**: backend, worker, frontend, postgres, redis, mlflow, loki, alloy, prometheus, gpu-exporter, tempo, grafana | 🔴 pretežko |
| 12 | Brez obveznega računa | JWT avtentikacija s signupom in e-poštno potrditvijo je **vgrajena v API** | 🟡 treba izklopiti |
| — | Hitrost | README sam priznava: difuzija **6–18 minut** za manjše območje | 🔴 neuporabno za mobilno aplikacijo |
| 13 | FLUX / Qwen / SAM2 / SAM3 / BiRefNet | nič od tega | 🔴 |

### Zaključek o osнови

**Repo je dobra ARHITEKTURNA referenca, ni pa uporabna koda za tvoj cilj.**

Razlogi: (a) nima geometrije, ki je srce tvoje zahteve; (b) njegov generativni del temelji na SD1.5+IP-Adapter, ki po zasnovi *ne more* ohraniti točnega vzorca ograje; (c) infrastruktura (11 storitev + JWT) je v nasprotju z zahtevo »backend mora biti samostojen« in »brez obveznega računa«; (d) nima Androida, torej bi 100 % odjemalca napisal na novo v vsakem primeru.

**Odločitev:** ne forknemo monolita. Naredimo **nov, minimalen backend**, ki (1) prevzame iz repoja dokazane vzorce — LaMa za odstranitev, MobileSAM/SAM za segmentacijo, rembg-slog odstranjevanja ozadja, async-job pattern, color matching — in (2) zamenja šibki generativni del z modelom iz leta 2026 ter doda **geometrijsko hrbtenico**, ki je v izvirniku ni. Izvirni MIT repo ostane v `research/base/` z licenco nedotaknjeno, v `README` in `NOTICE` pa je naveden kot vir navdiha (zahteva 17).

---

## 2. Drugi pregledani projekti

| Repo | ⭐ | Zadnji push | Licenca | Zakaj da / ne |
|---|---|---|---|---|
| `Sanster/IOPaint` | 23.319 | 2025-04-29, **ARHIVIRAN** | Apache-2.0 | Odličen vir LaMa/PowerPaint logike, a **arhiviran** → ne gradimo nanj; LaMa uporabimo neposredno |
| `advimman/lama` | 10.276 | 2025-02-05 | Apache-2.0 | ✅ original LaMa — uporabljen za odstranitev stare ograje (teče na CPU) |
| `ZhengPeng7/BiRefNet` | 4.217 | 2026-09-02 | **MIT** | ✅ najboljša odprta izbira za izrez izdelka; obstaja ONNX (`onnx-community/BiRefNet_lite-ONNX`) → lahko teče **na telefonu** |
| `danielgatis/rembg` | 24.847 | 2026-09-20 | MIT | ✅ uporabljen v osnovnem repoju; privzeti `u2net` je slabši od BiRefNet |
| `briaai/RMBG-2.0` | 608k prenosov | 2026-04-06 | **`other` = bria-rmbg (nekomercialno)** | ❌ **zavrnjeno** zaradi licence — kljub temu, da je kakovost vrhunska |
| `facebookresearch/sam3` | 11.762 | 2026-09-18 | **SAM License (ni OSI)** | 🟡 SAM 3.1 (27. 3. 2026) ima **exemplar prompts** = lahko segmentiraš »to ograjo« z eno referenčno sliko. Komercialno dovoljeno, a ni odprta licenca → samo na **strežniku** in samo kot opcija |
| `facebookresearch/sam2` | 19.909 | 2026-05-30 | **Apache-2.0** | ✅ varnejša licenčna izbira za strežniško segmentacijo |
| `lllyasviel/IC-Light` | 8.529 | 2025-02-20 | Apache-2.0 (v1) | 🟡 v1 je Apache-2.0, **v2 (FLUX-based) je nekomercialen** → za osvetljevanje uporabimo v1 ali FLUX.2 Klein |
| `Comfy-Org/ComfyUI` | 134.583 | 2026-09-23 | **GPL-3.0** | 🟡 podprt kot **opcijski** backend; ker je GPL, ga *ne vgrajujemo* v naš backend (samo kličemo prek HTTP), da ne okužimo licence |
| `black-forest-labs/flux2` | 2.702 | 2026-03-12 | **Apache-2.0** (klein 4B) | ✅ **izbrani difuzijski model** |
| `QwenLM/Qwen-Image` | 8.364 | 2026-02-10 | Apache-2.0 | ✅ **rezervni** difuzijski model (multi-image) |
| `Picsart-AI-Research/MI-GAN` | 693 | 2026-09-14 | MIT | ✅ lahek inpainting, primeren za mobilni/CPU način |
| `T8RIN/ImageToolbox` | 14.732 | 2026-09-22 | Apache-2.0 | 🟡 referenca za Android-side obdelavo slik in ONNX integracijo |
| `googlesamples/mediapipe` (interactive segmentation) | — | 2026 | Apache-2.0 | ✅ **segmentacija na telefonu** brez strežnika |
| AR/Sceneform projekti (`chayanforyou/ARFurniture`, `abinovarghese/Interior-Design-AR`, `SimformSolutionsPvtLtd/SSSceneFormSdkSample`) | majhne | 2023–2024 | različne | ❌ **neustrezno**: to so 3D/ARCore aplikacije s 3D modeli. Ti ne moreš vstaviti *svoje fotografirane* ograje, ker potrebuješ 3D model. Naš primer je 2D-fotorealizem. |

**Pomembna ugotovitev:** na GitHubu **ni** odprtokodne Android aplikacije, ki bi delala »fotografiraj prostor → fotografiraj svoj izdelek → vstavi referenco s perspektivo«. Vsi zadetki so AR/3D. To pomeni, da je niša prosta — in da moramo Android del napisati sami.

---

## 3. Modeli — kaj je izbrano in zakaj

### 3.1 Odstranitev stare ograje (»clean plate«)
**Izbrano: LaMa** (`advimman/lama`, Apache-2.0).
- Deluje **na CPU** (za manjše maske sprejemljivo), na GPU < 1 s.
- Za strukture, kot so fasada, omet, beton, steklo, je LaMa še vedno zelo močna in *ne dodaja novih objektov*.
- Alternativa za hitrost: **MI-GAN** (MIT). PowerPaint v2 je boljši pri »object removal«, a težji.

### 3.2 Izrez izdelka iz fotografije skladišča
1. 🟢 **lokalno, brez modela**: Otsu + največja povezana komponenta + morfologija + feather + defringe (implementirano v `PureCore.removeBackgroundLocal`, **testirano**).
2. 🟢 **na telefonu**: BiRefNet-lite ONNX prek `onnxruntime-android` (model se prenese posebej, ~100 MB).
3. 🟡 **na strežniku**: BiRefNet (MIT) — najboljša kakovost.
4. ❌ RMBG-2.0 — licenca nekomercialna.

### 3.3 Segmentacija stare ograje na fotografiji balkona
1. 🟢 **na telefonu**: MediaPipe **Interactive Segmenter** (Apache-2.0, model ~350 kB / ~5 MB) — tap → maska. Uporabnik jo popravi s čopičem.
2. 🟢 čopič +/−, pravokotnik, flood fill (vedno na voljo, tudi brez modela).
3. 🟡 **na strežniku**: MobileSAM (kot v osnovnem repoju) ali **SAM 3.1 z exemplar promptom** (segmentiraj »takšno ograjo« na podlagi referenčne slike). SAM License dovoljuje komercialno rabo, ni pa OSI — zato samo strežnik in samo kot opcija.

### 3.4 AI finalizacija (robovi, svetloba, sence)
**Izbrano: FLUX.2 [klein] 4B** (`black-forest-labs/FLUX.2-klein-4B`)

| Merilo | FLUX.2 klein 4B | Qwen-Image-Edit-2511 | SD1.5-inpaint + IP-Adapter (osnovni repo) |
|---|---|---|---|
| Licenca | **Apache-2.0** ✅ | **Apache-2.0** ✅ | CreativeML OpenRAIL-M 🟡 |
| Večreferenčno urejanje | ✅ | ✅ (do 3 slike) | delno (koncept, ne piksli) |
| VRAM | ~8 GB (README) / ~13 GB (model card) | 8 GB (GGUF) – 16 GB (FP8) – 40 GB (BF16) | ~4 GB |
| Hitrost | **pod 1 s** (4-koračna distilacija) | sekunde (Lightning 4-step) – ~500 s za 6 slik | 6–18 **minut** (podatek avtorja repoja) |
| Ohranjanje točnega videza | dobro, a **ne pikselno** | dobro, a **ne pikselno** | slabo (prerisuje) |
| Inpainting z masko | da (ComfyUI / diffusers) | da | da |

**Ključna odločitev:** noben difuzijski model **ne more zagotoviti** pikselne identitete izdelka. Zato difuzija pri nas **ni** mehanizem vstavljanja, ampak samo **finalizacija**:

```
referenčna fotografija ograje
   → izrez (BiRefNet)
   → homografija na 4 vogale (deterministično, piksli so ohranjeni)
   → barvno/svetlobno ujemanje (LAB, deterministično)
   → senca stika (deterministično)
   ── rezultat: GEOMETRIJSKI NAČIN, brez AI, brez strežnika 🟢
   → [opcijsko] difuzija z nizko jakostjo (strength 0.25–0.45) SAMO znotraj maske
   → [obvezno] po difuziji vrni vse piksle IZVEN maske iz originala (hard-composite)
```

Zadnja vrstica je implementirana in **testirana**: `changedOutsideMask == 0` tako na sintetični kot na realni sliki.

### 3.5 Kaj zavestno NE uporabimo
- **IP-Adapter** (kot v osnovnem repoju) — prenaša slog/koncept, ne vzorca. Za ograjo z določenim vzorcem letvic je to narobe orodje.
- **RMBG-2.0** — licenca.
- **FLUX.2 [dev] 32B / klein 9B** — nekomercialna licenca oz. H100.
- **IC-Light v2** — nekomercialen.
- **ComfyUI kot vgrajen del backenda** — GPL-3.0; uporabimo ga lahko kot *zunanji* proces.

---

## 4. Ali deluje lokalno? (zahteva 12)

| Nivo | Kaj teče | Plačilo | Zmogljivost |
|---|---|---|---|
| 🟢 **L0 — samo telefon** | kamera, čopič/pravokotnik/flood-fill, MediaPipe tap-segmentacija, Otsu izrez, homografija, LAB barvno ujemanje, senca stika, piramidno inpaintanje, shranjevanje projektov, primerjava variant | **0 €**, brez strežnika, brez interneta, brez računa | deluje takoj, rezultat je determinističen; brez pravega «relightinga» |
| 🟢 **L0+ — telefon z ONNX** | + BiRefNet-lite izrez na napravi | 0 € (enkrat ~100 MB model) | bistveno boljši izrez izdelka |
| 🟡 **L1 — tvoj strežnik** | + LaMa odstranitev, SAM/MobileSAM segmentacija, BiRefNet, FLUX.2 klein 4B finalizacija | 0 € licence; **GPU z ~8–13 GB VRAM** (RTX 3060 12GB / 4070 / 3090) ali najem ~0,3–1 €/h | 3–15 s na vizualizacijo |
| 🟡 **L1-CPU** | LaMa + geometrija na CPU | 0 € | 5–40 s (brez difuzije) |
| 🔴 **L2 — zunanji API** | Replicate / fal.ai / BFL API za FLUX ali Qwen | plačljivo po sliki | **ni obvezno in ni privzeto**; vklopi se samo, če sam nastaviš ključ v backendu |

Aplikacija **nikoli** ne zahteva: OpenAI ključa, Replicate ključa, Midjourneyja, naročnine ali uporabniškega računa. Brez strežnika deluje v načinu L0.

---

## 5. Android arhitektura

```
┌────────────────────────────── ANDROID (Kotlin + Jetpack Compose) ─────────────────────────────┐
│                                                                                                │
│  UI (6 korakov)          Domain                     Imaging (čisti Kotlin, testabilno)         │
│  ──────────────          ──────                     ──────────────────────────────────────────  │
│  HomeScreen              AppNav/Route               PureCore.kt   ← homografija, warp,         │
│  SceneScreen  (1)        ProjectController            composite, LAB, senca, inpaint,          │
│  ProductScreen (2)       ProjectStore (JSON+datoteke) removeBackgroundLocal, metrike           │
│  MaskScreen   (3)        ApiClient (HttpURLConnection) Homography.kt ← DLT 4-točkovna          │
│  PlaceScreen  (4)                                    MaskEditor  ← čopič +/-, undo/redo, fill  │
│  ResultScreen (5,6)      OnDeviceSegmenter          GeometryEngine ← Bitmap ovoj nad PureCore  │
│  SettingsScreen          BackgroundRemoverEngine                                               │
│                                                                                                │
│  Odvisnosti: Compose Material3 · CameraX · MediaPipe tasks-vision · onnxruntime-android        │
│              kotlinx-serialization · Coil                                                      │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
                                    │  REST + multipart (slike, maska, placement.json, prompt)
                                    ▼
┌──────────────────────── LASTEN BACKEND (FastAPI, samostojen) ─────────────────────────────────┐
│  /health   /remove-background   /segment   /remove   /finalize   /compare                     │
│  Providerji: GEOMETRY (brez AI) · LAMA · FLUX2_KLEIN_4B · QWEN_EDIT_2511 · SDXL_IPA · API 🔴  │
│  Brez Postgres/Redis/S3/JWT — datoteke na disku + SQLite (opcijsko) + asyncio Queue            │
│  Obvezna zaščita: po AI koraku se piksli IZVEN maske prepišejo iz originala + metrika leakage │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Zakaj takšna delitev
- **Geometrija je v Kotlinu, ne v Pythonu.** Tako je predogled takojšen (brez čakanja na strežnik), deluje brez interneta, in **ista matematika** se lahko preveri z JVM testi (`tools/jvmtest/`), ki tečejo brez Androida.
- **AI je izoliran za vtičnico `provider`.** Zamenjava modela ne spremeni aplikacije.
- **`returnMaskedOnly` / hard-composite** zagotavlja zahtevo 9 tudi če se model »razide«.

---

## 6. Kaj je treba razviti (obseg dela)

| Modul | Status | Opomba |
|---|---|---|
| `Homography.kt` (DLT + inverz + validacija) | ✅ **narejeno, prevedeno, testirano** | 22/22 testov |
| `PureCore.kt` (warp, composite, LAB, senca, inpaint, Otsu izrez, metrike) | ✅ **narejeno, testirano** | vključno z realno sliko |
| `MaskEditor` (čopič, undo/redo, fill, pravokotnik) | ✅ napisano | čaka Android build |
| `ApiClient` | ✅ napisano | čaka Android build |
| `OnDeviceSegmenter` (MediaPipe) | ✅ napisano | model se prenese ob prvem zagonu |
| UI: 6 zaslonov + domov + nastavitve | 🔨 v izdelavi | Compose |
| Shramba projektov (`project.json`, `original.jpg`, …) | ✅ napisano | |
| Backend `/health /remove-background /remove /segment /finalize` | 🔨 v izdelavi | FastAPI, brez Postgresa/Redisa |
| Provider FLUX.2 Klein / Qwen | 🔨 v izdelavi | diffusers; brez GPU v tem okolju **ni izvedljivo testirati** |
| Primerjava PREJ\|POTEM in A\|B\|C\|D | 🔨 v izdelavi | |
| APK (debug/release) | ⚠️ **ovira okolja** | gradbeno okolje ima **1 GB RAM** (cgroup) → `compileDebugKotlin` pade z OOM. Rešitev: GitHub Actions (`ubuntu-latest`, 7 GB) — workflow je priložen |
| Realni testi 1–5 z AI finalizacijo | ⚠️ **ni izvedljivo tu** | potreben GPU. Priložen je `backend/scripts/test_pipeline.py`, ki ga zaženeš na svojem GPU-ju |

### Kaj je bilo v tem okolju dejansko preverjeno
- ✅ geometrija: homografija preslika 4 vogale z napako **5,7·10⁻¹⁴** px
- ✅ inverz homografije vrne izhodišče
- ✅ degeneriran štirikotnik (podvojen vogal) je zavrnjen z izjemo
- ✅ sRGB↔LAB round-trip: max napaka **0,00/255**, L(črna)=0, L(bela)=100, L(siva 118)=49,6
- ✅ **zaščita originala**: `changedOutsideMask = 0` na sintetični **in realni** fotografiji
- ✅ piksli zunaj maske so **bitno enaki** originalu (0 razlik)
- ✅ senca stika potemni območje pod izdelkom (R 180 → 144)
- ✅ lokalni Otsu izrez: sredica α=255, ozadje α=0, bbox natančen (40,60,160,140)
- ✅ lokalno inpaintanje zmanjša odstopanje od originala z Δ 157,7 → **6,2**
- ✅ realna slika: izrez črne alu ograje iz fotografije 926×406, vstavljenih 33.358 px
- ❌ **AI finalizacija (FLUX.2/Qwen) ni bila preizkušena** — v tem okolju ni GPU-ja. Trditev, da deluje, bi bila laž; zato je označena kot *neoverjena* in priložen je testni skript za tvoj GPU.

---

## 7. Viri

- Osnovni repo: https://github.com/nazarpalamarenkoo-ui/AI-Photo-Object-Editor (MIT) — klon v `research/base/`
- FLUX.2 klein 4B: https://huggingface.co/black-forest-labs/FLUX.2-klein-4B · blog: https://bfl.ai/blog/flux2-klein-towards-interactive-visual-intelligence (15. 1. 2026)
- FLUX.2 repo: https://github.com/black-forest-labs/flux2 (Apache-2.0)
- Qwen-Image-Edit-2511: https://huggingface.co/Qwen/Qwen-Image-Edit-2511 (Apache-2.0)
- SAM 3 / 3.1: https://github.com/facebookresearch/sam3 · https://ai.meta.com/blog/segment-anything-model-3/ · licenca: https://github.com/facebookresearch/sam3/blob/main/LICENSE
- SAM 2: https://github.com/facebookresearch/sam2 (Apache-2.0)
- BiRefNet: https://github.com/ZhengPeng7/BiRefNet (MIT) · ONNX: https://huggingface.co/onnx-community/BiRefNet_lite-ONNX
- LaMa: https://github.com/advimman/lama (Apache-2.0) · IOPaint (arhiviran): https://github.com/Sanster/IOPaint
- IC-Light: https://github.com/lllyasviel/IC-Light (v2 nekomercialen — https://github.com/lllyasviel/IC-Light/discussions/98)
- MediaPipe Interactive Segmenter za Android: https://developers.google.com/edge/mediapipe/solutions/vision/interactive_segmenter/android
- MI-GAN: https://github.com/Picsart-AI-Research/MI-GAN (MIT)
- ComfyUI (GPL-3.0): https://github.com/Comfy-Org/ComfyUI
