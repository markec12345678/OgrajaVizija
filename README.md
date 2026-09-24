# 🏗 OgrajaVizija

[![android-apk](https://github.com/markec12345678/OgrajaVizija/actions/workflows/android.yml/badge.svg)](https://github.com/markec12345678/OgrajaVizija/actions/workflows/android.yml)

**»Kako bo moj dejanski balkon izgledal z mojo dejansko ograjo?«**

Android aplikacija + samostojen AI backend za fotorealistično vizualizacijo izdelkov na fotografiji.
Referenčna fotografija izdelka se **preslika in sestavi** (ne generira), AI pa samo
porobi robove, uskladi svetlobo in doda sence — vse pod strogo zaščito originalne fotografije.

```
1 📷 Fotografiraj balkon   →  2 📷 Fotografiraj svojo ograjo   →  3 ✏️ Označi staro ograjo
4 📐 Prilagodi položaj (4 vogali)  →  5 ✨ Ustvari vizualizacijo  →  6 💾 Shrani / primerjaj A|B|C|D
```

| Nivo | Kaj teče | Cena |
|---|---|---|
| 🟢 **LOKALNO** | izrez izdelka, maska, tap-segmentacija (MediaPipe), homografija/perspektiva, barvno ujemanje, senca, shranjevanje, primerjava | **0 €**, brez strežnika, brez računa, brez interneta |
| 🟡 **LASTEN STREŽNIK** | LaMa odstranitev stare ograje, BiRefNet izrez, SAM/MobileSAM, AI finalizacija (FLUX.2 klein 4B / Qwen-Image-Edit) | 0 € licence; GPU ~8–13 GB VRAM (ali CPU brez difuzije) |
| 🔴 **ZUNANJI API** | ni podprt in **namerno onemogočen** v backendu | — |

---

## Kaj je preverjeno (in kaj ni)

| Trditev | Status | Dokaz |
|---|---|---|
| Homografija iz 4 vogalov je matematično pravilna | ✅ | `tools/jvmtest` — napaka 5,7·10⁻¹⁴ px, 26/26 testov |
| Original izven maske ostane **bitno enak** | ✅ | `changedOutsideMask = 0` na sintetični **in realni** fotografiji |
| Lokalni izrez izdelka (Otsu) deluje | ✅ | α(sredica)=255, α(ozadje)=0, bbox natančen |
| Lokalno inpaintanje (clean plate) deluje | ✅ | Δ od originala 157,7 → 6,2 |
| Celotna Kotlin koda (24 datotek, Compose) se prevede | ✅ | kotlinc 2.0.21 + Compose + serialization plugin, 0 napak |
| Celoten lokalni pipeline na realni sliki | ✅ | `tools/jvmtest/out/real_*` (pred/po/clean plate; generirano lokalno, ni v repoju) |
| **APK zgrajen** | ✅ **DA** (GitHub Actions) | run `35959990105`: vsi koraki success; artifacta **apk-debug** (86,8 MB) in **apk-release-unsigned** (80,6 MB). Lokalni sandbox z 1 GB RAM ne zmore AGP — zato CI. |
| **AI finalizacija (FLUX.2/Qwen) preizkušena** | ⚠️ **NE** | potreben GPU (~8–13 GB VRAM); priložen `backend/scripts/test_pipeline.py`, ki preveri tudi zaščito originala (`leakage_ratio == 0`) |

> Načelo (zahteva 16): ničesar ne trdimo, česar nismo preizkusili. Zgoraj je meja med
> *dokazano* in *pripravljeno za testiranje na tvojem GPU-ju*.

---

## Repo struktura

```
android/            Kotlin + Jetpack Compose aplikacija (minSdk 26)
  app/src/main/java/si/ograjavizija/app/
    imaging/        PureCore.kt (homografija, warp, LAB, senca, inpaint, Otsu, metrike)
                    Homography.kt, MaskEditor.kt, BackgroundRemover.kt, BitmapIo.kt
    data/           Models.kt, ProjectStore.kt, ProjectController.kt, AppState.kt
    segmentation/   OnDeviceSegmenter.kt (MediaPipe Interactive Segmenter)
    network/        ApiClient.kt (multipart REST do lastnega backenda)
    ui/             6 korakov + domov + nastavitve (Compose)
backend/            samostojen FastAPI backend (brez Postgresa/Redisa/JWT)
  app/main.py       /health /remove-background /remove /segment /finalize
  app/providers/    GEOMETRY 🟢 · LAMA 🟢 · FLUX2_KLEIN_4B 🟡 · QWEN_EDIT_2511 🟡
  scripts/test_pipeline.py   realni testi 1–5 + metrika zaščite originala
tools/jvmtest/      JVM testi jedra (brez Androida): run_core_tests.sh
docs/               01-RAZISKAVA · 02-ARHITEKTURA · 03-TESTI · 04-BUILD
.github/workflows/  android.yml — zgradi debug+release APK + zažene JVM teste
research/base/      klon predlaganega osnovnega repoja (MIT) — samo referenca
```

---

## APK

Najlažje: **Actions → android-apk → zadnji uspešen run → Artifacts** (`apk-debug`, `apk-release-unsigned`).
Debug APK namestiš z `adb install app-debug.apk`. Release je za sideload/teste podpisan z debug ključem;
za Play dodaj svoj keystore v `app/build.gradle.kts`.

## Hiter začetek

### Aplikacija (🟢 brez strežnika)
```bash
cd android
# Android Studio: odpri mapo android/  ALI
gradle :app:assembleDebug          # potrebuje >= 8 GB RAM
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Backend (🟡, samostojen)
```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
./run_server.sh                     # CPU: GEOMETRY + LAMA(Telea) delujeta takoj
# z GPU-jem:
pip install torch diffusers transformers accelerate
OVIZ_PROVIDER=FLUX2_KLEIN_4B ./run_server.sh
```
V aplikaciji: **Nastavitve → naslov strežnika → Preveri povezavo**.

### Testi jedra (brez Androida, ~30 s)
```bash
cd tools/jvmtest && bash run_core_tests.sh [scene.jpg product.jpg placement.txt]
```

---

## Zakaj taka arhitektura (povzetek iz docs/01-RAZISKAVA.md)

* Predlagani osnovni repo (`nazarpalamarenkoo-ui/AI-Photo-Object-Editor`, MIT) je **arhitekturna
  referenca**, ne osnova za fork: nima perspektivne geometrije (lepi pravokotnik na bbox),
  njegov generativni del je SD1.5+IP-Adapter (prerisuje izdelek), infra zahteva 11 storitev
  in JWT račun — vse v nasprotju s tvojimi zahtevami.
* **Difuzija nikoli ne vstavlja izdelka.** Izdelek vstavi deterministična homografija;
  difuzija (FLUX.2 klein 4B, Apache-2.0) samo *finalizira* znotraj maske z nizko jakostjo,
  nato backend **obvezno** vrne vse piksle izven maske iz originala.
* Licence: FLUX.2 klein 4B ✅ Apache-2.0 · Qwen-Image-Edit ✅ Apache-2.0 · BiRefNet ✅ MIT ·
  LaMa ✅ Apache-2.0 · MediaPipe ✅ Apache-2.0 · **RMBG-2.0 ❌ izločen (nekomercialna licenca)** ·
  SAM 3 🟡 samo strežnik (SAM License ni OSI) · IC-Light v2 ❌ (nekomercialen).

Celotna analiza z zvezdicami, datumih in licencami: **[docs/01-RAZISKAVA.md](docs/01-RAZISKAVA.md)**.

---

## Licenca

Koda tega repoja: **Apache-2.0**. Obvestila tretjih oseb: **NOTICE**.
Izvirnega MIT repoja ne odstranjujemo: klon ostane v `research/base/` z licenco.
