# 04 · Build navodila

## Android APK

### Lokalno (priporočeno; potrebuje ≥ 8 GB RAM)
```bash
cd android
# 1) Android Studio: File → Open → izberi mapo android/  (sync + Run)
# 2) ali CLI:
gradle :app:assembleDebug      # ali: ./gradlew, ce ga generiras z 'gradle wrapper'
adb install -r app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease    # release je podpisan z debug kljucem (za sideload testov)
```
Za pravi release signing dodaš `signingConfigs` v `app/build.gradle.kts` in Play keystore.

### CI (brez lokalnega okolja)
Push na GitHub → Actions → `android-apk` → artifact `apk-debug` / `apk-release-unsigned`.

### Znana omejitev sandbox okolja (dokumentirano, ne skrito)
Gradnja v okolju z **1 GB RAM (cgroup)** pade pri `:app:compileDebugKotlin`
("Gradle build daemon disappeared" = OOM kill). Preverjeno: konfiguracija, resursi in
manifest se zgradijo; pade šele Kotlin compile, ki za Compose projekt potrebuje ~1,2–1,5 GB.
Zato:
* celotna koda je **tipno preverjena** z `kotlinc 2.0.21` + Compose plugin + serialization
  plugin proti pravemu `android.jar` (35) in vsem androidx/mediapipe artefaktom — **0 napak**;
* jedro (Homography, PureCore) je **dejansko pognano** v JVM testih (26/26);
* APK dostavi CI runner s 7 GB RAM.

## Backend
```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt          # 🟢 jedro: FastAPI + OpenCV + Pillow
./run_server.sh                          # http://0.0.0.0:8787  (docs: /docs)

# 🟡 z GPU-jem (AI finalizacija):
pip install torch diffusers transformers accelerate
OVIZ_PROVIDER=FLUX2_KLEIN_4B OVIZ_DEVICE=cuda ./run_server.sh
```
VRAM: FLUX.2 klein 4B ≈ 8–13 GB · Qwen-Image-Edit-2511 ≈ 8 GB (GGUF) do 16 GB (FP8).
Brez GPU-ja providerja javita `available: false` in `/health` ponudi samo 🟢 poti.

## Modeli, ki se prenesejo ob prvi uporabi
| Model | Velikost | Kje | Licenca |
|---|---|---|---|
| MediaPipe interactive_segmentation.task | ~2–6 MB | telefon (ob prvem tapu) | Apache-2.0 |
| BiRefNet-lite ONNX (opcijsko na telefonu) | ~100 MB | telefon (ročni uvoz) | MIT |
| mobile_sam.pt (opcijsko na strežniku) | ~40 MB | strežnik | Apache-2.0 |
| FLUX.2-klein-4B | ~8–10 GB | strežnik (HF cache) | Apache-2.0 |
| Qwen-Image-Edit-2511 | ~20 GB (BF16) / ~14 GB (GGUF) | strežnik | Apache-2.0 |

## JVM testi jedra
```bash
cd tools/jvmtest
bash run_core_tests.sh                          # sinteticni testi
bash run_core_tests.sh scene.jpg product.jpg placement.txt   # + realna slika
```
`placement.txt` format (normalizirane koordinate):
```
corners=0.555,0.155 0.885,0.355 0.885,0.610 0.555,0.430
poly=0.545,0.140 0.895,0.345 0.895,0.625 0.545,0.445
```
